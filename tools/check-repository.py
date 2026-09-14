#!/usr/bin/env python3
"""Run bounded repository checks without printing secret values."""

from __future__ import annotations

import argparse
import codecs
import hashlib
import os
import re
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


MAX_SCAN_BYTES = 1_048_576
BINARY_PROBE_BYTES = 8192
# A byte-order mark is the one thing that says "this text is more than one byte per character" before
# the NUL heuristic below can mistake those bytes for a binary file. The four-byte marks are tested
# first and must be: a UTF-32LE mark is `ff fe 00 00`, whose first two bytes are exactly a UTF-16LE
# mark, so testing two bytes first reads a UTF-32 file as UTF-16 and finds nothing in it.
UTF32_BOMS = (b"\xff\xfe\x00\x00", b"\x00\x00\xfe\xff")
UTF16_BOMS = (b"\xff\xfe", b"\xfe\xff")
SKIP_PARTS = {".git", ".local-tools", ".gradle", "build", "out", "generated"}

# This is the SHA-256 of the wrapper JAR fetched from the official Gradle source
# at https://raw.githubusercontent.com/gradle/gradle/v9.7.1/gradle/wrapper/gradle-wrapper.jar.
EXPECTED_WRAPPER_SHA256 = "7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d"
EXPECTED_DISTRIBUTION_SHA256 = "acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a"

ACTION_REFERENCE = re.compile(r"^\s*(?:-\s*)?uses:\s*([^\s#]+)")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
IMMUTABLE_ACTION_SHA = re.compile(r"^[0-9a-f]{40}$")

SECRET_PATTERNS = (
    ("private-key", re.compile(r"-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----")),
    ("openai-key", re.compile(r"\bsk-(?:proj|live)-[A-Za-z0-9_-]{20,}\b")),
    ("groq-key", re.compile(r"\bgsk_[A-Za-z0-9_-]{20,}\b")),
    ("google-key", re.compile(r"\bAIza[0-9A-Za-z_-]{30,}\b")),
    ("github-token", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,})\b")),
    ("slack-token", re.compile(r"\bxox[baprs]-[A-Za-z0-9-]{20,}\b")),
    ("aws-access-key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    (
        "credential-assignment",
        re.compile(
            r"(?i)\b(?:api[_-]?key|secret[_-]?key|access[_-]?token|bearer|password)"
            r"\b\s*[:=]\s*['\"]?(?!test\b|dummy\b|example\b|placeholder\b|your[_-])"
            r"[A-Za-z0-9+/=_-]{24,}"
        ),
    ),
)

MAIN_FORBIDDEN = (
    ("cleartext-url", re.compile(r"(?i)\bhttp://(?!schemas\.android\.com/)")),
    ("cleartext-traffic", re.compile(r"(?i)usesCleartextTraffic\s*=\s*[\"']true[\"']")),
    ("test-provider", re.compile(r"(?i)(?:androidx\.test|MockWebServer|FixtureProvider|test\.documents)")),
)

# A line number is right for one version of a file and points at other code after the next change above it. By round
# 19, eight of the thirteen in docs/DEFECTS.md did, one of them sending a reader who looked for a hash check into
# `rollback`. The documents name the function or quote the expression instead. The patterns take the forms such a
# reference comes in, a link anchor like `#L314` among them, and so also fire on text that only looks like one, a
# port alone in backticks or a stack frame quoted whole; such text is reworded, not let through.
DOCS_FORBIDDEN = (
    ("line-number", re.compile(r"\.(?:kts?|java|py|xml|toml|ya?ml|sh|md|json|properties|pro|gradle)(?::| ?L)\d+")),
    ("line-number", re.compile(r"`:\d+`")),
    ("line-number", re.compile(r"#L\d+")),
    ("line-number", re.compile(r"(?i)\b(?:zeilen?|lines?) \d+|\bz\. ?\d+")),
)


@dataclass(frozen=True)
class Issue:
    path: str
    line: int | None
    reason: str


@dataclass
class Tally:
    """What the run actually opened, so the closing line can say it instead of implying it."""

    scanned: int = 0
    binary: int = 0
    truncated: int = 0
    unreadable: int = 0
    # None where no git index says which files may run, and then the closing line says that instead.
    scripts: int | None = None

    def __str__(self) -> str:
        modes = (
            "no git index to read file modes from"
            if self.scripts is None
            else f"{self.scripts} scripts checked for their executable bit"
        )
        return (
            f"{self.scanned} files read, {self.binary} skipped as binary, "
            f"{self.truncated} read only to {MAX_SCAN_BYTES} bytes, {self.unreadable} unreadable, {modes}"
        )


def _skipped(path: Path) -> bool:
    return bool(SKIP_PARTS.intersection(path.parts))


def _tracked_paths(root: Path) -> list[Path]:
    try:
        result = subprocess.run(
            ["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return _filesystem_paths(root)
    # During a merge git lists a conflicted path once per side, and each would be read and counted again.
    return [root / item for item in dict.fromkeys(result.stdout.decode().split("\0")) if item]


def _filesystem_paths(root: Path) -> list[Path]:
    paths: list[Path] = []
    for directory, directories, filenames in os.walk(root, topdown=True, followlinks=False):
        directory_path = Path(directory)
        directories[:] = [
            name for name in directories
            if name not in SKIP_PARTS and not (directory_path / name).is_symlink()
        ]
        paths.extend(
            directory_path / name
            for name in filenames
            if not (directory_path / name).is_symlink()
        )
    return paths


def _paths(root: Path, tracked_only: bool) -> list[Path]:
    return _tracked_paths(root) if tracked_only else _filesystem_paths(root)


def _encoding_from_mark(head: bytes) -> str:
    """The encoding a byte-order mark declares, or UTF-8 when there is none.

    Order matters, and getting it wrong is worse than not looking at all: `ff fe 00 00` is a UTF-32LE
    mark and `ff fe` is a UTF-16LE mark, so a two-byte test matches both and decodes a UTF-32 file into
    text with a NUL between every character. Nothing matches such text, and the file is counted as read.
    """
    if head[:4] in UTF32_BOMS:
        return "utf-32"
    if head[:2] in UTF16_BOMS:
        return "utf-16"
    return "utf-8"


def _read_text(path: Path, tally: Tally | None = None) -> str | None:
    """Return a file's text, or None when it holds bytes no pattern here could match.

    The decision comes from the bytes, not from a list of suffixes. A list is remembered rather than
    checked: until 11 September 2026 this function opened nineteen suffixes and passed silently over
    everything else, so `gradlew`, `.gitignore`, `LICENSE` and every other extension-less file in the
    tree went unread while the run still printed PASS. A private key saved as `.pem` would have been
    among them. A NUL byte near the start is the one thing that says "not text", and it is what a
    `.so`, a `.png` and the packed extractor all carry.

    Except in UTF-16 and UTF-32, where every ASCII character is stored as two or four bytes and some of
    them are NUL, so the first letter of such a file trips that heuristic and the whole file goes unread.
    UTF-16 is not an exotic encoding on this machine: Notepad's "Unicode" option and older PowerShell
    redirections both write it. A byte-order mark is therefore checked before the NUL probe, widest mark
    first — see `_encoding_from_mark`. Without a mark, either stays indistinguishable from binary here,
    which is a limit of this check rather than a property of it.
    """
    counter = tally if tally is not None else Tally()
    try:
        with path.open("rb") as handle:
            head = handle.read(BINARY_PROBE_BYTES)
            encoding = _encoding_from_mark(head)
            if encoding == "utf-8" and b"\0" in head:
                counter.binary += 1
                return None
            raw = head + handle.read(MAX_SCAN_BYTES - len(head) + 1)
    except OSError:
        counter.unreadable += 1
        return None
    if len(raw) > MAX_SCAN_BYTES:
        counter.truncated += 1
        raw = raw[:MAX_SCAN_BYTES]
    counter.scanned += 1
    return raw.decode(encoding, errors="replace")


def _issue_path(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def _scan_text(path: Path, root: Path, tally: Tally) -> list[Issue]:
    text = _read_text(path, tally)
    if text is None:
        return []
    issues: list[Issue] = []
    for line_number, line in enumerate(text.splitlines(), 1):
        for label, pattern in SECRET_PATTERNS:
            if pattern.search(line):
                issues.append(Issue(_issue_path(path, root), line_number, f"secret pattern: {label}"))
        if "/src/main/" in f"/{_issue_path(path, root)}":
            for label, pattern in MAIN_FORBIDDEN:
                if pattern.search(line):
                    issues.append(Issue(_issue_path(path, root), line_number, f"forbidden in main: {label}"))
        if _issue_path(path, root).startswith("docs/") and path.suffix == ".md":
            for label, pattern in DOCS_FORBIDDEN:
                if pattern.search(line):
                    issues.append(Issue(_issue_path(path, root), line_number, f"forbidden in docs: {label}"))
    return issues


def _check_actions(root: Path) -> list[Issue]:
    issues: list[Issue] = []
    workflow_root = root / ".github" / "workflows"
    if not workflow_root.is_dir():
        return issues
    for path in sorted(workflow_root.glob("*.y*ml")):
        # Deliberately outside the coverage tally: the walk in `check_repository` has already counted
        # this file, and counting it again here would overstate what the run opened.
        text = _read_text(path)
        if text is None:
            continue
        for line_number, line in enumerate(text.splitlines(), 1):
            match = ACTION_REFERENCE.match(line)
            if not match:
                continue
            reference = match.group(1)
            if reference.startswith("./"):
                continue
            if "@" not in reference or not IMMUTABLE_ACTION_SHA.fullmatch(reference.rsplit("@", 1)[1]):
                issues.append(Issue(_issue_path(path, root), line_number, "external action is not pinned to a commit SHA"))
    return issues


def _check_wrapper(root: Path) -> list[Issue]:
    issues: list[Issue] = []
    wrapper = root / "gradle" / "wrapper" / "gradle-wrapper.jar"
    properties = root / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if not wrapper.is_file():
        issues.append(Issue("gradle/wrapper/gradle-wrapper.jar", None, "Gradle wrapper JAR is missing"))
    elif hashlib.sha256(wrapper.read_bytes()).hexdigest() != EXPECTED_WRAPPER_SHA256:
        issues.append(Issue("gradle/wrapper/gradle-wrapper.jar", None, "Gradle wrapper JAR SHA-256 mismatch"))
    if not properties.is_file():
        issues.append(Issue("gradle/wrapper/gradle-wrapper.properties", None, "Gradle wrapper properties are missing"))
        return issues
    text = properties.read_text(encoding="utf-8")
    values = dict(line.split("=", 1) for line in text.splitlines() if "=" in line and not line.startswith("#"))
    distribution_hash = values.get("distributionSha256Sum", "")
    if distribution_hash != EXPECTED_DISTRIBUTION_SHA256 or not SHA256.fullmatch(distribution_hash):
        issues.append(Issue("gradle/wrapper/gradle-wrapper.properties", None, "Gradle distribution SHA-256 is missing or unexpected"))
    if not values.get("distributionUrl", "").startswith(r"https\://services.gradle.org/distributions/gradle-9.7.1-"):
        issues.append(Issue("gradle/wrapper/gradle-wrapper.properties", None, "Gradle distribution URL is not the approved 9.7 HTTPS source"))
    return issues


def _check_dependency_verification(root: Path) -> list[Issue]:
    path = root / "gradle" / "verification-metadata.xml"
    if not path.is_file():
        return [Issue("gradle/verification-metadata.xml", None, "dependency verification metadata is missing")]
    # A second read of a file the walk has already counted, as in `_check_actions` above.
    text = _read_text(path)
    if text is None or "<verification-metadata" not in text:
        return [Issue("gradle/verification-metadata.xml", None, "dependency verification metadata is invalid")]
    return []


NOTICE_FILES = ("THIRD_PARTY_NOTICES.md", "app/src/main/assets/legal/THIRD_PARTY_NOTICES.txt")


def _check_notice_versions(root: Path) -> list[Issue]:
    """Flag a version in the third-party notices that the version catalogue does not have.

    Both notices, one of them shown in the app, name some dependencies with their version. `d994c23` moved
    Bouncy Castle to 1.86 in the catalogue, and both went on naming 1.85. A name counts when it is a key of the
    catalogue's [versions] table, in backticks or as a word of its own, followed by a version with a dot.
    """
    catalogue = root / "gradle" / "libs.versions.toml"
    text = _read_text(catalogue) if catalogue.is_file() else None
    if text is None or "[versions]" not in text:
        return []
    table = text.split("[versions]", 1)[1].split("\n[", 1)[0]
    versions = {key.lower(): value for key, value in re.findall(r'^([\w.-]+)\s*=\s*"([^"]+)"', table, re.M)}
    issues: list[Issue] = []
    for name in NOTICE_FILES:
        path = root / name
        notice = _read_text(path) if path.is_file() else None
        for line_number, line in enumerate((notice or "").splitlines(), 1):
            for key, found in re.findall(r"(?<![\w.-])`?([\w.-]+)`?\s+(\d+(?:\.\d+)+)(?![\w.-])", line):
                expected = versions.get(key.lower())
                if expected is not None and found != expected:
                    reason = f"names {key} {found}, the version catalogue has {expected}"
                    issues.append(Issue(name, line_number, reason))
    return issues


def _check_notice_copies(root: Path) -> list[Issue]:
    """Flag the first line where the app's copy of the third-party notices differs from the notices.

    `812c4dc` took DocumentFile out of the copy the app shows, together with the dependency, and the notices went
    on naming it until round 21. Lines are compared, so a checkout that writes CRLF into one of them makes no
    difference. When only one of the two exists, the missing one is flagged; a tree with neither has nothing to
    compare.
    """
    present = [(root / name).is_file() for name in NOTICE_FILES]
    if not any(present):
        return []
    if not all(present):
        missing, existing = NOTICE_FILES[present.index(False)], NOTICE_FILES[present.index(True)]
        return [Issue(missing, None, f"is missing while {existing} exists")]
    original, copy = ((_read_text(root / name) or "").splitlines() for name in NOTICE_FILES)
    if original == copy:
        return []
    differing = (number for number, (left, right) in enumerate(zip(original, copy), 1) if left != right)
    line_number = next(differing, min(len(original), len(copy)) + 1)
    return [Issue(NOTICE_FILES[1], line_number, f"differs from {NOTICE_FILES[0]}, which it copies for the app")]


def _check_executable_scripts(root: Path, tally: Tally) -> list[Issue]:
    """Flag a tracked script that starts with a shebang but that git records as not executable.

    Git keeps one permission of a file, whether it may run, and a checkout on Linux or macOS restores it.
    `0f6db67` took it from this file, because the script that staged that commit wrote every file as
    100644, and nothing noticed: every documented call starts with `python3`. A shebang in a `src`
    directory belongs to a resource such as the packed yt-dlp, which nothing runs from the tree. A path in
    the middle of a merge has an index entry per side and conflict markers in the tree, so neither the mode
    nor the first line that will be committed is known yet; such a path is flagged once and not counted. So is a
    path the index has and the tree cannot give, deleted or kept out with skip-worktree, which may be a script too.
    """
    try:
        result = subprocess.run(
            ["git", "-C", str(root), "ls-files", "--stage", "-z", "--"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return []
    issues: list[Issue] = []
    scripts = 0
    unmerged: set[str] = set()
    for entry in result.stdout.decode().split("\0"):
        if not entry:
            continue
        details, path = entry.split("\t", 1)
        mode, _, stage = details.split(" ")
        if mode not in ("100644", "100755") or "src" in Path(path).parts or _skipped(Path(path)):
            continue
        if stage != "0":
            if path not in unmerged:
                unmerged.add(path)
                issues.append(Issue(path, None, "unmerged in the git index, so its mode cannot be checked"))
            continue
        try:
            with (root / path).open("rb") as handle:
                if handle.read(2) != b"#!":
                    continue
        except OSError:
            issues.append(Issue(path, None, "is in the git index but cannot be read, so its mode cannot be checked"))
            continue
        scripts += 1
        if mode != "100755":
            issues.append(Issue(path, None, f"script with a shebang has git mode {mode}, not 100755"))
    tally.scripts = scripts
    return issues


def check_repository(root: Path, *, tracked_only: bool = True, tally: Tally | None = None) -> list[Issue]:
    counter = tally if tally is not None else Tally()
    issues: list[Issue] = []
    for path in _paths(root, tracked_only):
        if path.is_file() and not path.is_symlink() and not _skipped(path.relative_to(root)):
            issues.extend(_scan_text(path, root, counter))
    issues.extend(_check_actions(root))
    issues.extend(_check_wrapper(root))
    issues.extend(_check_dependency_verification(root))
    issues.extend(_check_notice_versions(root))
    issues.extend(_check_notice_copies(root))
    # Only a run over what git tracks has modes to check; the walk over a plain directory has none.
    if tracked_only:
        issues.extend(_check_executable_scripts(root, counter))
    return sorted(set(issues), key=lambda issue: (issue.path, issue.line or 0, issue.reason))


def _self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="sourcescribe-check-") as directory:
        root = Path(directory)
        (root / "src/main").mkdir(parents=True)
        (root / "src/androidTest").mkdir(parents=True)
        (root / ".github/workflows").mkdir(parents=True)
        (root / ".local-tools/large-cache").mkdir(parents=True)
        (root / "src/main/Secrets.kt").write_text('val key = "sk-proj-' + "A" * 24 + '"\n', encoding="utf-8")
        (root / "src/main/AndroidManifest.xml").write_text(
            '<application android:usesCleartextTraffic="true">\n'
            '<provider android:name="FixtureProvider" />\n', encoding="utf-8"
        )
        (root / "src/androidTest/Fixture.kt").write_text('val url = "http://127.0.0.1"\n', encoding="utf-8")
        (root / ".github/workflows/unpinned.yml").write_text("- uses: example/action@main\n", encoding="utf-8")
        (root / ".local-tools/large-cache/ignored.txt").write_text('sk-proj-' + "B" * 24, encoding="utf-8")
        # A file with no suffix at all, which is what `gradlew` and `LICENSE` are. Before the scan
        # decided text from bytes, this one was never opened and the run still said PASS.
        (root / "src/main/launcher").write_bytes(b"#!/bin/sh\nTOKEN=gsk_" + b"C" * 24 + b"\n")
        # And the same bytes inside something binary, which must stay closed.
        (root / "src/main/blob.bin").write_bytes(b"\0\0\0gsk_" + b"D" * 24)
        # Plain text with a NUL inside every character, which is what UTF-16 is. Until round 13 this
        # file was ruled binary on its first letter and never searched.
        (root / "src/main/utf16.kt").write_bytes(('val key = "gsk_' + "E" * 24 + '"\n').encode("utf-16"))
        # Four bytes per character, and a mark whose first two bytes are a UTF-16 mark. Read as UTF-16 it
        # decodes into text with a NUL between every character, which matches nothing and looks scanned.
        (root / "src/main/utf32.kt").write_bytes(('val key = "gsk_' + "F" * 24 + '"\n').encode("utf-32"))
        # The same thing the other way round, because `.encode("utf-32")` writes the little-endian mark on
        # every machine this runs on: the big-endian entry of UTF32_BOMS had no test through it at all.
        # Its first two bytes are 00 00, so the two-byte check round 13 shipped would not have mistaken
        # this file for UTF-16 — it would have left it to the NUL probe and been honest about it. Which is
        # why the round-14 story holds for little-endian only, and why this line exists.
        (root / "src/main/utf32be.kt").write_bytes(
            codecs.BOM_UTF32_BE + ('val key = "gsk_' + "G" * 24 + '"\n').encode("utf-32-be")
        )
        # The three forms of line number docs/DEFECTS.md used until round 19 and the ones a reviewer of round 20 got
        # past them, one per line, then words that only look like one, and a line number outside docs/, which this
        # rule leaves alone.
        (root / "docs").mkdir()
        (root / "docs/Defects.md").write_text(
            "`Manager.kt:314`\nund `:271`\ngeprüft in Zeile 24\n[Code](../extractor/Manager.kt#L314)\n"
            "steht in Manager.kt L314\nsiehe gradlew#L12\nsteht in Z. 24\nZEILE 24 ist betroffen\n",
            encoding="utf-8",
        )
        (root / "docs/Fine.md").write_text(
            "Die Kostenzeile ist zweizeilig, und Zeilennummern wandern; `file()` prüft `validSlot`.\n"
            "Das gilt z. B. für L10n, Level L2, eine Pipeline 2 und die Headline 3.\n",
            encoding="utf-8",
        )
        (root / "src/androidTest/Notes.md").write_text("`Manager.kt:314`\n", encoding="utf-8")
        tally = Tally()
        issues = check_repository(root, tracked_only=False, tally=tally)
        reasons = {issue.reason for issue in issues}
        assert "secret pattern: openai-key" in reasons
        assert "forbidden in main: cleartext-traffic" in reasons
        assert "forbidden in main: test-provider" in reasons
        assert "external action is not pinned to a commit SHA" in reasons
        assert not any(issue.path.startswith(".local-tools/") for issue in issues)
        assert not any(issue.path.startswith("src/androidTest/") for issue in issues)
        assert any(issue.path == "src/main/launcher" for issue in issues), "extension-less file went unread"
        assert not any(issue.path == "src/main/blob.bin" for issue in issues), "binary file was read as text"
        assert any(issue.path == "src/main/utf16.kt" for issue in issues), "UTF-16 file went unread"
        assert any(issue.path == "src/main/utf32.kt" for issue in issues), "UTF-32 file went unread"
        assert any(issue.path == "src/main/utf32be.kt" for issue in issues), "UTF-32BE file went unread"
        flagged = {
            issue.line for issue in issues
            if issue.path == "docs/Defects.md" and issue.reason == "forbidden in docs: line-number"
        }
        assert flagged == set(range(1, 9)), f"line numbers in docs flagged on lines {sorted(flagged)}"
        assert not any(issue.path in ("docs/Fine.md", "src/androidTest/Notes.md") for issue in issues)
        # Exactly, not at least. A lower bound is satisfied by a file going unread, which is the one
        # thing this counter exists to make visible.
        assert tally == Tally(scanned=11, binary=1), str(tally)
    # Modes live in the git index, so this part builds one: a script git may run, one it may not, a file without a
    # shebang and a shebang under src/, of which only the second is flagged. Then two paths in the middle of a merge,
    # with an index entry per side, one of them with its conflict in the shebang line: each is flagged once as
    # unmerged, read once, and counted as no script. A script deleted from the tree after it was added cannot be read:
    # it is flagged for that and counted as no script either.
    with tempfile.TemporaryDirectory(prefix="sourcescribe-modes-") as directory:
        root = Path(directory)
        (root / "tools").mkdir()
        (root / "src/main/res/raw").mkdir(parents=True)
        (root / "tools/runs.sh").write_bytes(b"#!/bin/sh\n")
        (root / "tools/stopped.py").write_bytes(b"#!/usr/bin/env python3\n")
        (root / "tools/notes.txt").write_bytes(b"no shebang\n")
        (root / "tools/gone.sh").write_bytes(b"#!/bin/sh\n")
        (root / "src/main/res/raw/packed").write_bytes(b"#!/usr/bin/env python3\n")

        def git(*arguments: str, stdin: bytes | None = None) -> bytes:
            return subprocess.run(
                ["git", "-C", str(root), *arguments], input=stdin, check=True, capture_output=True, timeout=30
            ).stdout

        git("init", "-q")
        git("add", "--", ".")
        git("update-index", "--chmod=+x", "--", "tools/runs.sh")
        git("update-index", "--chmod=-x", "--", "tools/stopped.py", "src/main/res/raw/packed")
        blob = git("hash-object", "-w", "--", "tools/runs.sh").decode().strip()
        sides = "".join(
            f"{mode} {blob} {stage}\t{path}\n"
            for path in ("tools/conflicted.sh", "tools/marked.sh")
            for mode, stage in (("100755", 1), ("100644", 2), ("100755", 3))
        )
        git("update-index", "--index-info", stdin=sides.encode())
        (root / "tools/conflicted.sh").write_bytes(
            b"#!/bin/sh\n<<<<<<< ours\necho one\n=======\necho two\n>>>>>>> theirs\n"
        )
        (root / "tools/marked.sh").write_bytes(b"<<<<<<< ours\n#!/bin/sh\n=======\n#!/bin/bash\n>>>>>>> theirs\n")
        (root / "tools/gone.sh").unlink()
        tally = Tally()
        issues = check_repository(root, tally=tally)
        flagged = [issue.path for issue in issues if "git mode" in issue.reason]
        assert flagged == ["tools/stopped.py"], str(issues)
        unmerged = [issue.path for issue in issues if "unmerged" in issue.reason]
        assert unmerged == ["tools/conflicted.sh", "tools/marked.sh"], str(issues)
        unreadable = [issue.path for issue in issues if "cannot be read" in issue.reason]
        assert unreadable == ["tools/gone.sh"], str(issues)
        assert tally.scripts == 2, str(tally)
        assert (tally.scanned, tally.binary) == (6, 0), str(tally)
    # The notices have to name the catalogue's versions, in backticks or as a word of their own. A name the
    # catalogue does not know is left alone, and an entry outside its [versions] table is no version.
    with tempfile.TemporaryDirectory(prefix="sourcescribe-notices-") as directory:
        root = Path(directory)
        (root / "gradle").mkdir()
        (root / "gradle/libs.versions.toml").write_text(
            '[versions]\nbcpg = "1.86"\njunit = "4.13.2"\n\n[libraries]\nbcprov = "1.99"\n', encoding="utf-8"
        )
        (root / "THIRD_PARTY_NOTICES.md").write_text(
            "| Bouncy Castle | `bcpg` 1.85, `bcprov` 1.85.2 |\n(`bcpg` 1.86) und JUnit 4.13.2, EJS 0.8.0\n",
            encoding="utf-8",
        )
        issues = _check_notice_versions(root)
        expected = [Issue("THIRD_PARTY_NOTICES.md", 1, "names bcpg 1.85, the version catalogue has 1.86")]
        assert issues == expected, str(issues)
        # The app's copy has to equal these notices line by line. CRLF makes no difference; a changed line does, and
        # so does a missing last line. A copy without the notices it copies is flagged as well.
        notices = (root / "THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8")
        copy = root / NOTICE_FILES[1]
        copy.parent.mkdir(parents=True)
        copy.write_bytes(notices.replace("\n", "\r\n").encode("utf-8"))
        assert _check_notice_copies(root) == [], str(_check_notice_copies(root))
        differs = [Issue(NOTICE_FILES[1], 2, "differs from THIRD_PARTY_NOTICES.md, which it copies for the app")]
        copy.write_bytes(notices.replace("1.86", "1.85").encode("utf-8"))
        assert _check_notice_copies(root) == differs, str(_check_notice_copies(root))
        copy.write_bytes(notices.split("\n", 1)[0].encode("utf-8") + b"\n")
        assert _check_notice_copies(root) == differs, str(_check_notice_copies(root))
        (root / "THIRD_PARTY_NOTICES.md").unlink()
        missing = [Issue("THIRD_PARTY_NOTICES.md", None, f"is missing while {NOTICE_FILES[1]} exists")]
        assert _check_notice_copies(root) == missing, str(_check_notice_copies(root))
    print("PASS self-test")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)
    if args.self_test:
        _self_test()
        return 0
    root = args.root.resolve()
    tally = Tally()
    issues = check_repository(root, tally=tally)
    if issues:
        for issue in issues:
            location = f":{issue.line}" if issue.line is not None else ""
            print(f"FAIL {issue.path}{location}: {issue.reason}")
        print(f"     coverage: {tally}")
        return 1
    # The counts are part of the result, not decoration. A PASS over nothing looks exactly like a PASS
    # over everything, and from 7 to 11 September 2026 this check looked at nineteen suffixes while
    # reading as if it had looked at the repository.
    print(f"PASS repository checks ({tally})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

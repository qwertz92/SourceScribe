#!/usr/bin/env python3
"""Run bounded repository checks without printing secret values."""

from __future__ import annotations

import argparse
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

    def __str__(self) -> str:
        return (
            f"{self.scanned} files read, {self.binary} skipped as binary, "
            f"{self.truncated} read only to {MAX_SCAN_BYTES} bytes, {self.unreadable} unreadable"
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
    return [root / item for item in result.stdout.decode().split("\0") if item]


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


def check_repository(root: Path, *, tracked_only: bool = True, tally: Tally | None = None) -> list[Issue]:
    counter = tally if tally is not None else Tally()
    issues: list[Issue] = []
    for path in _paths(root, tracked_only):
        if path.is_file() and not path.is_symlink() and not _skipped(path.relative_to(root)):
            issues.extend(_scan_text(path, root, counter))
    issues.extend(_check_actions(root))
    issues.extend(_check_wrapper(root))
    issues.extend(_check_dependency_verification(root))
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
        # Exactly, not at least. A lower bound is satisfied by a file going unread, which is the one
        # thing this counter exists to make visible.
        assert tally == Tally(scanned=7, binary=1), str(tally)
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

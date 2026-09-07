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
SKIP_PARTS = {".git", ".local-tools", ".gradle", "build", "out", "generated"}
TEXT_SUFFIXES = {
    ".c", ".cc", ".cpp", ".gradle", ".h", ".html", ".java", ".json", ".kts", ".kt",
    ".md", ".properties", ".py", ".sh", ".toml", ".txt", ".xml", ".yaml", ".yml",
}

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


def _read_text(path: Path) -> str | None:
    try:
        if path.stat().st_size > MAX_SCAN_BYTES or path.suffix.lower() not in TEXT_SUFFIXES:
            return None
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return None


def _issue_path(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def _scan_text(path: Path, root: Path) -> list[Issue]:
    text = _read_text(path)
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
    text = _read_text(path)
    if text is None or "<verification-metadata" not in text:
        return [Issue("gradle/verification-metadata.xml", None, "dependency verification metadata is invalid")]
    return []


def check_repository(root: Path, *, tracked_only: bool = True) -> list[Issue]:
    issues: list[Issue] = []
    for path in _paths(root, tracked_only):
        if path.is_file() and not path.is_symlink() and not _skipped(path.relative_to(root)):
            issues.extend(_scan_text(path, root))
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
        issues = check_repository(root, tracked_only=False)
        reasons = {issue.reason for issue in issues}
        assert "secret pattern: openai-key" in reasons
        assert "forbidden in main: cleartext-traffic" in reasons
        assert "forbidden in main: test-provider" in reasons
        assert "external action is not pinned to a commit SHA" in reasons
        assert not any(issue.path.startswith(".local-tools/") for issue in issues)
        assert not any(issue.path.startswith("src/androidTest/") for issue in issues)
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
    issues = check_repository(root)
    if issues:
        for issue in issues:
            location = f":{issue.line}" if issue.line is not None else ""
            print(f"FAIL {issue.path}{location}: {issue.reason}")
        return 1
    print("PASS repository checks")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

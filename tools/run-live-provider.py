#!/usr/bin/env python3
"""Run the opt-in real-provider Android test without exposing either local API key."""

import argparse
import re
import shlex
import subprocess
import sys
from pathlib import Path


TEST_COMPONENT = "app.sourcescribe.debug.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = "app.sourcescribe.data.LiveGroqTranscriptionTest"
TEST_METHODS = {
    "groq": "aShortPublicVideoIsTranscribedByGroqAndStoredWithItsProvenance",
    "assemblyai": "aShortPublicVideoIsTranscribedByAssemblyAiAndStoredWithItsProvenance",
}
PROJECT_ROOT = Path(__file__).resolve().parents[1]
PRODUCT_MAX_AUDIO_SECONDS = 36_000
ASSEMBLYAI_MAX_CLIP_SECONDS = 30
DEFAULT_CLIP_SECONDS = 30


def read_keys(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        raise ValueError("could not read the local env file") from None
    for line in lines:
        name, separator, raw = line.partition("=")
        if not separator or name.strip() not in {"GROQ-KEY", "ASSEMBLYAI-KEY"}:
            continue
        value = raw.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
            value = value[1:-1]
        if value:
            values[name.strip()] = value
    return values


def redact(text: str, secrets: list[str]) -> str:
    for secret in sorted((item for item in secrets if item), key=len, reverse=True):
        text = text.replace(secret, "[REDACTED]")
    return text


def output_text(value: str | bytes | None) -> str:
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return value or ""


def instrumentation_succeeded(output: str, returncode: int, provider: str) -> bool:
    if returncode != 0 or any(marker in output for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED:")):
        return False
    if re.search(r"(?m)^INSTRUMENTATION_STATUS_CODE:\s*-\d+\s*$", output):
        return False
    if not re.search(r"(?m)^\s*OK \(\s*1 test\s*\)\s*$", output):
        return False
    expected = provider.upper()
    if not re.search(rf"(?m)^INSTRUMENTATION_STATUS: live\.provider={re.escape(expected)}\s*$", output):
        return False
    return bool(re.search(r"(?m)^INSTRUMENTATION_STATUS: live\.outcome=SUCCESS(?:_WITH_WARNINGS)?\s*$", output))


def instrumentation_command(
    provider: str,
    api_key: str,
    source_url: str,
    max_clip_seconds: int,
    model: str | None = None,
    audio_track: str | None = None,
) -> str:
    instrumentation = [
        "am", "instrument", "-w",
        "-e", "class", f"{TEST_CLASS}#{TEST_METHODS[provider]}",
        "-e", "sourcescribeLiveProvider", provider,
        "-e", "liveProviderKey", api_key,
        "-e", "publicSourceUrl", source_url,
        "-e", "liveMaxClipSeconds", str(max_clip_seconds),
    ]
    if model:
        instrumentation.extend(("-e", "liveModel", model))
    if audio_track is not None:
        instrumentation.extend(("-e", "liveAudioTrackId", audio_track))
    return " ".join(shlex.quote(value) for value in instrumentation + [TEST_COMPONENT])


def self_test_runner() -> None:
    successful = "\n".join((
        "INSTRUMENTATION_STATUS: live.provider=GROQ",
        "INSTRUMENTATION_STATUS: live.outcome=SUCCESS",
        "OK (1 test)",
        "INSTRUMENTATION_CODE: -1",
    ))
    assert instrumentation_succeeded(successful, 0, "groq")
    assert not instrumentation_succeeded(successful + "\nFAILURES!!!", 0, "groq")
    assert not instrumentation_succeeded(
        successful.replace("INSTRUMENTATION_CODE: -1", "INSTRUMENTATION_STATUS_CODE: -3\nINSTRUMENTATION_CODE: -1"),
        0,
        "groq",
    )
    assert not instrumentation_succeeded("", 0, "groq")
    track = "139-drc ' ; $(must-stay-a-single-argument)"
    command = instrumentation_command(
        "assemblyai", "self-test-key", "https://example.test/watch?v=video", 30, audio_track=track,
    )
    assert shlex.split(command) == [
        "am", "instrument", "-w",
        "-e", "class", f"{TEST_CLASS}#{TEST_METHODS['assemblyai']}",
        "-e", "sourcescribeLiveProvider", "assemblyai",
        "-e", "liveProviderKey", "self-test-key",
        "-e", "publicSourceUrl", "https://example.test/watch?v=video",
        "-e", "liveMaxClipSeconds", "30",
        "-e", "liveAudioTrackId", track,
        TEST_COMPONENT,
    ]


def save_report(path: Path | None, text: str, secrets: list[str]) -> None:
    if path is None:
        return
    root = (PROJECT_ROOT / ".local-tools").resolve()
    destination = (path if path.is_absolute() else PROJECT_ROOT / path).resolve()
    if not destination.is_relative_to(root):
        raise ValueError("report path must be inside .local-tools")
    try:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(redact(text, secrets), encoding="utf-8")
    except OSError:
        raise ValueError("could not write the redacted report") from None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("provider", nargs="?", choices=("groq", "assemblyai"))
    parser.add_argument("source_url", nargs="?", help="exact public YouTube URL to test")
    parser.add_argument("--self-test", action="store_true", help="test result parsing and argument quoting without adb or provider calls")
    parser.add_argument("--serial", help="explicit adb device serial")
    parser.add_argument("--adb", type=Path, help="path to adb executable")
    parser.add_argument("--model", help="optional Groq model override")
    parser.add_argument("--audio-track", help="exact audio track ID from this source's resolved tracks")
    parser.add_argument("--max-clip-seconds", type=int, help="source duration ceiling; defaults to 30 seconds")
    parser.add_argument("--timeout-seconds", type=int, default=1_200)
    parser.add_argument("--report", type=Path, help="write redacted output below .local-tools/")
    args = parser.parse_args()
    if args.self_test:
        if args.provider or args.source_url or args.serial or args.adb or args.audio_track is not None:
            parser.error("--self-test cannot be combined with a provider run")
        return args
    if not args.provider or not args.source_url:
        parser.error("provider and source_url are required unless --self-test is used")
    if not args.serial or not args.adb:
        parser.error("--serial and --adb are required for a provider run")
    if not args.serial.strip() or any(character.isspace() for character in args.serial):
        parser.error("--serial must be one explicit device serial")
    if args.timeout_seconds < 1:
        parser.error("--timeout-seconds must be positive")
    if args.model and args.provider != "groq":
        parser.error("--model is supported only for Groq")
    if args.audio_track is not None and (not args.audio_track.strip() or len(args.audio_track) > 100):
        parser.error("--audio-track must be a nonempty source track ID of at most 100 characters")
    maximum = args.max_clip_seconds if args.max_clip_seconds is not None else DEFAULT_CLIP_SECONDS
    ceiling = ASSEMBLYAI_MAX_CLIP_SECONDS if args.provider == "assemblyai" else PRODUCT_MAX_AUDIO_SECONDS
    if not 1 <= maximum <= ceiling:
        parser.error(f"--max-clip-seconds must be between 1 and {ceiling} for {args.provider}")
    args.max_clip_seconds = maximum
    return args


def main() -> int:
    args = parse_args()
    if args.self_test:
        self_test_runner()
        print("live-provider runner self-test passed")
        return 0
    if not args.adb.is_file():
        print("adb executable path does not exist", file=sys.stderr)
        return 2
    try:
        keys = read_keys(PROJECT_ROOT / ".env.local")
    except ValueError as error:
        print(str(error), file=sys.stderr)
        return 2

    key_name = "GROQ-KEY" if args.provider == "groq" else "ASSEMBLYAI-KEY"
    api_key = keys.get(key_name, "")
    secrets = list(keys.values())
    if not api_key:
        print(f"{key_name} is missing from the local env file", file=sys.stderr)
        return 2

    remote_command = instrumentation_command(
        args.provider,
        api_key,
        args.source_url,
        args.max_clip_seconds,
        args.model,
        args.audio_track,
    )
    adb = str(args.adb)
    result_code = 1
    output = ""
    clear_output = ""
    clear_failed = False
    print(f"Running {args.provider} instrumentation on the explicitly selected device {args.serial}.")
    try:
        try:
            result = subprocess.run(
                [adb, "-s", args.serial, "shell", remote_command],
                capture_output=True,
                text=True,
                timeout=args.timeout_seconds,
                check=False,
            )
            output = output_text(result.stdout) + output_text(result.stderr)
            result_code = 0 if instrumentation_succeeded(output, result.returncode, args.provider) else 1
        except subprocess.TimeoutExpired as error:
            output = output_text(error.stdout) + output_text(error.stderr)
            output += f"\nInstrumentation timed out after {args.timeout_seconds} seconds.\n"
            result_code = 1
        except OSError:
            output = "Could not start adb instrumentation.\n"
            result_code = 1
    finally:
        try:
            cleared = subprocess.run(
                [adb, "-s", args.serial, "logcat", "-b", "all", "-c"],
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
            clear_output = output_text(cleared.stdout) + output_text(cleared.stderr)
            clear_failed = cleared.returncode != 0
        except (OSError, subprocess.TimeoutExpired) as error:
            clear_failed = True
            clear_output = f"Logcat clear failed: {type(error).__name__}.\n"

    safe_output = redact(output, secrets)
    if safe_output:
        print(safe_output, end="" if safe_output.endswith("\n") else "\n")
    if clear_failed:
        print("Failed to clear logcat on the selected device.", file=sys.stderr)
        if clear_output:
            print(redact(clear_output, secrets), file=sys.stderr, end="")
        result_code = 1
    report = f"provider={args.provider}\nserial={args.serial}\nexit={result_code}\n\n{safe_output}"
    try:
        save_report(args.report, report, secrets)
    except ValueError as error:
        print(str(error), file=sys.stderr)
        result_code = 1
    return result_code


if __name__ == "__main__":
    raise SystemExit(main())

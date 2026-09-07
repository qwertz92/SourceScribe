"""Supervise one trusted native command and reap all of its descendants."""

import ctypes
import os
import signal
import sys
import time


PR_SET_PDEATHSIG = 1
PR_SET_CHILD_SUBREAPER = 36
ACK_TIMEOUT_SECONDS = 2.0
TERM_GRACE_SECONDS = 0.5
KILL_GRACE_SECONDS = 0.4
_terminating = False


def _prctl(option, value):
    libc = ctypes.CDLL(None, use_errno=True)
    if libc.prctl(option, value, 0, 0, 0) != 0:
        raise OSError(ctypes.get_errno(), "prctl")


def _write_pid(path):
    temporary = path + ".tmp." + str(os.getpid())
    with open(temporary, "w", encoding="ascii") as pid_file:
        pid_file.write(str(os.getpid()))
        pid_file.flush()
        os.fsync(pid_file.fileno())
    os.replace(temporary, path)


def _handle_termination(_signum, _frame):
    global _terminating
    _terminating = True


def _process(pid):
    try:
        with open("/proc/%d/status" % pid, encoding="ascii") as status:
            fields = {}
            for line in status:
                name, separator, value = line.partition(":")
                if separator and name in ("PPid", "Uid", "State"):
                    fields[name] = value.strip().split()[0]
        return int(fields["PPid"]), int(fields["Uid"]), fields["State"]
    except (FileNotFoundError, KeyError, OSError, ValueError):
        return None


def _is_descendant(pid, supervisor, uid):
    seen = set()
    while pid > 1 and pid not in seen:
        seen.add(pid)
        record = _process(pid)
        if record is None or record[1] != uid:
            return False
        parent = record[0]
        if parent == supervisor:
            return True
        pid = parent
    return False


def _descendants(supervisor, uid):
    # ponytail: /proc is small for one app UID; replace with pidfds only when Android support permits it.
    descendants = []
    for entry in os.listdir("/proc"):
        if entry.isdigit():
            pid = int(entry)
            record = _process(pid)
            if record is not None and record[2] != "Z" and _is_descendant(pid, supervisor, uid):
                descendants.append(pid)
    return descendants


def _signal_descendants(supervisor, uid, signum):
    signalled = False
    for pid in _descendants(supervisor, uid):
        if not _is_descendant(pid, supervisor, uid):
            continue
        try:
            os.kill(pid, signum)
            signalled = True
        except ProcessLookupError:
            pass
    return signalled


def _reap():
    while True:
        try:
            pid, _status = os.waitpid(-1, os.WNOHANG)
            if pid == 0:
                return
        except ChildProcessError:
            return
        except InterruptedError:
            continue


def _wait_until_empty(supervisor, uid, deadline):
    while time.monotonic() < deadline:
        _reap()
        if not _descendants(supervisor, uid):
            return True
        time.sleep(0.01)
    _reap()
    return not _descendants(supervisor, uid)


def _cleanup(supervisor, uid):
    try:
        stop_deadline = time.monotonic() + 0.15
        while time.monotonic() < stop_deadline:
            if not _signal_descendants(supervisor, uid, signal.SIGSTOP):
                break
            time.sleep(0.01)

        _signal_descendants(supervisor, uid, signal.SIGTERM)
        _signal_descendants(supervisor, uid, signal.SIGCONT)
        if _wait_until_empty(supervisor, uid, time.monotonic() + TERM_GRACE_SECONDS):
            return True

        _signal_descendants(supervisor, uid, signal.SIGSTOP)
        _signal_descendants(supervisor, uid, signal.SIGKILL)
        _signal_descendants(supervisor, uid, signal.SIGCONT)
        return _wait_until_empty(supervisor, uid, time.monotonic() + KILL_GRACE_SECONDS)
    except OSError:
        return False


def _wait_for_ack(path, expected):
    deadline = time.monotonic() + ACK_TIMEOUT_SECONDS
    while not _terminating and time.monotonic() < deadline:
        try:
            with open(path, encoding="ascii") as ack_file:
                if ack_file.read().strip() == expected:
                    return True
        except FileNotFoundError:
            pass
        except OSError:
            return False
        time.sleep(0.005)
    return False


def _target_status(child):
    while not _terminating:
        try:
            pid, status = os.waitpid(child, os.WNOHANG)
        except InterruptedError:
            continue
        except ChildProcessError:
            return 125
        if pid == child:
            if os.WIFEXITED(status):
                return os.WEXITSTATUS(status)
            if os.WIFSIGNALED(status):
                return 128 + os.WTERMSIG(status)
            return 125
        time.sleep(0.01)
    return 128 + signal.SIGTERM


def main():
    if len(sys.argv) < 4 or sys.argv[1] not in ("--exec", "--python"):
        return 64

    mode, pid_path, target = sys.argv[1:4]
    ack_path = pid_path + ".ack"
    supervisor = os.getpid()
    parent = os.getppid()
    uid = os.getuid()
    try:
        if parent <= 1:
            return 125
        _prctl(PR_SET_PDEATHSIG, signal.SIGTERM)
        if os.getppid() != parent:
            return 125
        _prctl(PR_SET_CHILD_SUBREAPER, 1)
        os.setsid()
        signal.signal(signal.SIGTERM, _handle_termination)
        signal.signal(signal.SIGINT, _handle_termination)
        _write_pid(pid_path)
        if not _wait_for_ack(ack_path, str(supervisor)) or _terminating:
            return 128 + signal.SIGTERM if _terminating else 124

        child = os.fork()
        if child == 0:
            if _terminating:
                os._exit(128 + signal.SIGTERM)
            signal.signal(signal.SIGTERM, signal.SIG_DFL)
            signal.signal(signal.SIGINT, signal.SIG_DFL)
            try:
                _prctl(PR_SET_PDEATHSIG, signal.SIGTERM)
                if os.getppid() != supervisor:
                    os._exit(125)
                arguments = sys.argv[4:]
                if mode == "--python":
                    os.execv(sys.executable, [sys.executable, target, *arguments])
                os.execv(target, [target, *arguments])
            except (OSError, ValueError):
                os._exit(127)

        result = _target_status(child)
        return result if _cleanup(supervisor, uid) else 125
    except (OSError, ValueError):
        _cleanup(supervisor, uid)
        return 125
    finally:
        for path in (ack_path, pid_path):
            try:
                os.unlink(path)
            except OSError:
                pass


if __name__ == "__main__":
    sys.exit(main())

"""ADB device detection and management."""
import os
import shutil
import subprocess
from typing import Optional


def _find_adb() -> str:
    """Find adb binary, checking ANDROID_HOME if not on PATH."""
    adb = shutil.which("adb")
    if adb:
        return adb
    for env_var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        sdk = os.environ.get(env_var)
        if sdk:
            candidate = os.path.join(sdk, "platform-tools", "adb")
            if os.path.isfile(candidate):
                return candidate
    return "adb"


def check_adb() -> Optional[str]:
    """Check if adb is available. Returns None if OK, error string otherwise."""
    try:
        subprocess.run([_find_adb(), "version"], capture_output=True, check=True, timeout=5)
        return None
    except FileNotFoundError:
        return "adb not found. Install Android SDK platform-tools or set ANDROID_HOME."
    except subprocess.TimeoutExpired:
        return "adb timed out. Check your adb installation."
    except subprocess.CalledProcessError as e:
        return f"adb error: {e}"


def list_devices() -> list[dict]:
    """List connected Android devices via adb."""
    try:
        result = subprocess.run(
            [_find_adb(), "devices", "-l"],
            capture_output=True, text=True, timeout=10,
        )
        devices = []
        for line in result.stdout.strip().split("\n")[1:]:
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                serial = parts[0]
                info = {"serial": serial}
                for part in parts[2:]:
                    if ":" in part:
                        k, v = part.split(":", 1)
                        info[k] = v
                devices.append(info)
        return devices
    except Exception:
        return []


def get_device_info(serial: str = "") -> dict:
    """Get device properties (model, API level, etc.)."""
    cmd_prefix = [_find_adb()]
    if serial:
        cmd_prefix += ["-s", serial]

    info = {}
    props = {
        "model": "ro.product.model",
        "api_level": "ro.build.version.sdk",
        "android_version": "ro.build.version.release",
        "manufacturer": "ro.product.manufacturer",
    }
    for key, prop in props.items():
        try:
            r = subprocess.run(
                cmd_prefix + ["shell", "getprop", prop],
                capture_output=True, text=True, timeout=5,
            )
            info[key] = r.stdout.strip()
        except Exception:
            info[key] = "unknown"
    return info

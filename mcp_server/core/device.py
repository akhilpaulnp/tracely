"""ADB device detection and management."""
import subprocess
from typing import Optional


def check_adb() -> Optional[str]:
    """Check if adb is available. Returns None if OK, error string otherwise."""
    try:
        subprocess.run(["adb", "version"], capture_output=True, check=True, timeout=5)
        return None
    except FileNotFoundError:
        return "adb not found. Install Android SDK platform-tools."
    except subprocess.TimeoutExpired:
        return "adb timed out. Check your adb installation."
    except subprocess.CalledProcessError as e:
        return f"adb error: {e}"


def list_devices() -> list[dict]:
    """List connected Android devices via adb."""
    try:
        result = subprocess.run(
            ["adb", "devices", "-l"],
            capture_output=True, text=True, timeout=10,
        )
        devices = []
        for line in result.stdout.strip().split("\n")[1:]:
            if "\tdevice" in line:
                parts = line.split()
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
    cmd_prefix = ["adb"]
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

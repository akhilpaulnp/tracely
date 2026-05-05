"""Device API routes."""
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from tracely.core import device


async def get_devices(request: Request):
    err = device.check_adb()
    if err:
        return JSONResponse({"error": err, "devices": []})

    devices = device.list_devices()
    for d in devices:
        info = device.get_device_info(d["serial"])
        d.update(info)

    return JSONResponse({"devices": devices})


routes = [
    Route("/api/devices", get_devices),
]

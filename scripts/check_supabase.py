"""Проверка серверной настройки лабораторной №2 без сторонних библиотек."""

import json
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
properties = dict(
    line.strip().split("=", 1)
    for line in (ROOT / "local.properties").read_text().splitlines()
    if "=" in line and not line.lstrip().startswith("#")
)
base_url = properties["supabase.url"].rstrip("/")
key = properties["supabase.publishableKey"]


def get(path):
    request = Request(base_url + path, headers={"apikey": key})
    try:
        with urlopen(request, timeout=10) as response:
            return response.status, response.read().decode()
    except HTTPError as error:
        return error.code, error.read().decode()


try:
    settings_status, settings_body = get("/auth/v1/settings")
    profiles_status, _ = get("/rest/v1/profiles?select=id&limit=1")
except URLError as error:
    print(f"Сервер недоступен: {error.reason}")
    sys.exit(1)

settings = json.loads(settings_body) if settings_status == 200 else {}
checks = {
    "Таблица profiles создана": profiles_status in (200, 401, 403),
    "Google-провайдер включён": settings.get("external", {}).get("google") is True,
    "Подтверждение email отключено для полной демонстрации": settings.get("mailer_autoconfirm") is True,
}
for name, passed in checks.items():
    print(f"{'OK' if passed else 'НУЖНА НАСТРОЙКА'} — {name}")

sys.exit(0 if all(checks.values()) else 1)

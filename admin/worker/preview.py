"""Previews the admin dashboard locally with sample numbers.

    python3 admin/worker/preview.py            # http://localhost:8080

Serves the dashboard page straight out of worker.js, so what you see is exactly
what deploys; only /v1/stats is faked. No Cloudflare account or database needed.
"""
import json
import os
import random
import re
import sys
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PORT = int(os.environ.get("PORT", "8080"))
WORKER = Path(__file__).with_name("worker.js")
DAYS = 30


def dashboard_html() -> str:
    text = WORKER.read_text()
    start = text.index("const DASHBOARD_HTML = `") + len("const DASHBOARD_HTML = `")
    end = text.rindex("`;")
    return text[start:end]


def sample_stats():
    today = date.today()
    random.seed(7)
    series = []
    installs = []
    for offset in range(DAYS - 1, -1, -1):
        day = (today - timedelta(days=offset)).isoformat()
        growth = 1 + (DAYS - offset) / 18
        series.append({"day": day, "kind": "app_open", "total": int(21 * growth + random.random() * 6)})
        series.append({"day": day, "kind": "calc", "total": int(48 * growth + random.random() * 25)})
        series.append({"day": day, "kind": "ai", "total": int(7 * growth + random.random() * 4)})
        installs.append({"day": day, "total": int(4 * growth + random.random() * 3)})
    total = lambda kind: sum(r["total"] for r in series if r["kind"] == kind)  # noqa: E731
    return {
        "generatedAt": f"{today.isoformat()}T09:40:00Z",
        "days": DAYS,
        "totals": {"app_open": total("app_open"), "calc": total("calc"), "ai": total("ai")},
        "active": {"dau": 46, "wau": 214, "mau": 388, "installs": 517},
        "series": series,
        "installs": installs,
        "versions": [
            {"version": "1.0.0", "total": 402},
            {"version": "1.0.0-debug", "total": 115},
        ],
    }


class Handler(BaseHTTPRequestHandler):
    html = ""

    def _send(self, body: str, content_type: str) -> None:
        payload = body.encode("utf-8")
        self.send_response(200)
        self.send_header("content-type", content_type)
        self.send_header("content-length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):  # noqa: N802 (http.server API)
        if self.path.startswith("/v1/stats"):
            self._send(json.dumps(sample_stats()), "application/json; charset=utf-8")
        elif self.path == "/health":
            self._send(
                json.dumps(
                    {
                        "ok": True,
                        "service": "AI Calculator stats (preview)",
                        "database": "ok",
                        "secrets": {
                            "ADMIN_TOKEN": True,
                            "GEMINI_API_KEY": False,
                            "GOOGLE_WEB_CLIENT_ID": False,
                        },
                    }
                ),
                "application/json; charset=utf-8",
            )
        elif self.path in ("/", "/index.html"):
            self._send(self.html, "text/html; charset=utf-8")
        else:
            self.send_error(404)

    def log_message(self, *args):  # keep the console quiet
        pass


def main() -> int:
    try:
        Handler.html = dashboard_html()
    except ValueError:
        print("Could not find DASHBOARD_HTML in worker.js — is the file intact?", file=sys.stderr)
        return 1
    print(f"Dashboard preview on http://localhost:{PORT} (sample data, token ignored)")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

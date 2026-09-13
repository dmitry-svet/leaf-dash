#!/usr/bin/env python3
"""LeafDash log receiver.

The app POSTs batches of CSV log lines (Settings -> "Stream log to URL").
Lines are appended to logs/leafdash-YYYYMMDD.csv (header added once) and
echoed to stdout so a live tail is visible.

Usage: python3 tools/log_server.py [port]   (default 8765)
Point the app at http://<pc-ip>:<port>/log
"""
import datetime
import http.server
import pathlib
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
LOG_DIR = pathlib.Path(__file__).resolve().parent.parent / "logs"
HEADER = "t_ms,odoRaw,odoKm,speed,b6,sessDist,dist,soc,gids,ah,packV,packA,kwh,batC\n"


class Handler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(n).decode("utf-8", "replace").strip()
        if body:
            LOG_DIR.mkdir(exist_ok=True)
            path = LOG_DIR / f"leafdash-{datetime.date.today():%Y%m%d}.csv"
            new = not path.exists()
            with path.open("a") as fh:
                if new:
                    fh.write(HEADER)
                fh.write(body + "\n")
            lines = body.splitlines()
            print(f"[{datetime.datetime.now():%H:%M:%S}] +{len(lines)} lines, last: {lines[-1]}")
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print(f"LeafDash log server on 0.0.0.0:{PORT}, writing to {LOG_DIR}/")
    http.server.ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()

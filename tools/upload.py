#!/usr/bin/env python3
"""Simplest possible upload page: python3 tools/upload.py [port] [dest-dir]"""

import email.parser
import email.policy
import html
import os
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
DEST = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else "uploads")

PAGE = """<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Upload</title>
<style>
  body {{ font: 16px/1.5 system-ui, sans-serif; max-width: 32rem; margin: 4rem auto; padding: 0 1rem; }}
  input, button {{ font: inherit; }}
  button {{ margin-top: 1rem; padding: .5rem 1rem; }}
  p {{ color: #2a7; }}
</style>
<h1>Upload</h1>
<form method="post" enctype="multipart/form-data">
  <input type="file" name="file" multiple required>
  <button>Send</button>
</form>
{message}
"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.respond(PAGE.format(message=""))

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        headers = f"Content-Type: {self.headers['Content-Type']}\r\n\r\n".encode()
        message = email.parser.BytesParser(policy=email.policy.default).parsebytes(
            headers + self.rfile.read(length)
        )

        os.makedirs(DEST, exist_ok=True)
        saved = []
        for part in message.iter_parts():
            name = os.path.basename(part.get_filename() or "")
            if not name:
                continue
            with open(os.path.join(DEST, name), "wb") as f:
                f.write(part.get_payload(decode=True))
            saved.append(name)

        note = "Saved " + ", ".join(saved) if saved else "Nothing uploaded"
        self.respond(PAGE.format(message=f"<p>{html.escape(note)}</p>"))

    def respond(self, body):
        payload = body.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


if __name__ == "__main__":
    print(f"Uploading to {DEST}  —  http://0.0.0.0:{PORT}")
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()

import http.server
import socketserver
import webbrowser
import os

PORT = 8080
DIRECTORY = os.path.join(os.path.dirname(__file__), "web")

class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

print(f"Starting X Shot local preview server at http://localhost:{PORT}")
print("Press Ctrl+C to stop.")

try:
    webbrowser.open(f"http://localhost:{PORT}")
    with socketserver.TCPServer(("", PORT), Handler) as httpd:
        httpd.serve_forever()
except KeyboardInterrupt:
    print("\nServer stopped.")

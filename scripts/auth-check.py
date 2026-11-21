#!/usr/bin/env python3
"""Interactive Phase 2 check. Credentials and session tokens are never printed."""
import getpass
import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

def call(path, method="GET", payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE + path, method=method, headers=headers,
        data=None if payload is None else json.dumps(payload).encode())
    try:
        with opener.open(request, timeout=20) as response:
            return response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"{}")

def main():
    username = input("App username: ").strip()
    password = getpass.getpass("App login password: ")
    status, result = call("/auth/login", "POST", {"username": username, "password": password})
    del password
    if status != 200:
        raise SystemExit(f"Login failed: HTTP {status}; {result.get('message', '')}")
    token = result["accessToken"]
    print("PASS: login HTTP 200")
    try:
        status, user = call("/auth/me", token=token)
        assert status == 200, f"/auth/me returned {status}"
        print("PASS: current user; role =", user["role"])
        status, settings = call("/businessSettings/business", token=token)
        assert status == 200, f"Settings read returned {status}"
        print("PASS: settings read HTTP 200")
        # Resubmit existing values: verifies write permissions without changing shop details.
        payload = {key: value for key, value in settings.items() if key not in ("id", "currency")}
        status, result = call("/transactions/settings", "PUT", payload, token)
        expected = 200 if "admin" in user["roles"] else 403
        assert status == expected, f"Settings write returned {status}; expected {expected}"
        print(f"PASS: settings write permission HTTP {status}")
        status, result = call("/activityLogs?limit=5", token=token)
        expected = 200 if "admin" in user["roles"] else 403
        assert status == expected, f"Activity log returned {status}; expected {expected}"
        print(f"PASS: activity log permission HTTP {status}")
    finally:
        status, _ = call("/auth/logout", "POST", {}, token)
        assert status == 204, f"Logout returned {status}"
    status, _ = call("/auth/me", token=token)
    assert status == 401, f"Revoked session returned {status}"
    print("PASS: logout HTTP 204; revoked session HTTP 401")
    print("Phase 2 authentication check completed.")

if __name__ == "__main__":
    main()

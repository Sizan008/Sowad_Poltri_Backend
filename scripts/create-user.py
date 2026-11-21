#!/usr/bin/env python3
"""Create an operator/viewer/admin via the real authenticated API."""
import getpass
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location("auth_check", Path(__file__).with_name("auth-check.py"))
api = importlib.util.module_from_spec(spec)
spec.loader.exec_module(api)

def main():
    admin = input("Existing admin username: ").strip()
    password = getpass.getpass("Existing admin login password: ")
    status, result = api.call("/auth/login", "POST", {"username": admin, "password": password})
    del password
    if status != 200:
        raise SystemExit(f"Admin login failed: HTTP {status}")
    token = result["accessToken"]
    try:
        username = input("New username: ").strip()
        display_name = input("Display name: ").strip()
        role = input("Role (admin/operator/viewer): ").strip()
        password = getpass.getpass("New password (12+ characters, max 72 UTF-8 bytes): ")
        confirmation = getpass.getpass("Confirm password: ")
        if password != confirmation:
            raise SystemExit("Passwords do not match.")
        status, result = api.call("/users", "POST", {
            "username": username, "displayName": display_name,
            "password": password, "role": role}, token)
        del password, confirmation
        if status != 201:
            raise SystemExit(f"Create failed: HTTP {status}; {result.get('message', '')}")
        print("Created user:", result["username"], "role:", result["role"])
    finally:
        api.call("/auth/logout", "POST", {}, token)

if __name__ == "__main__":
    main()

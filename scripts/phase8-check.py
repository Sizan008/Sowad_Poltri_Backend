#!/usr/bin/env python3
"""Read-only Phase 8 smoke check. Login/logout create normal audit events."""
import getpass
import json
import sys
import urllib.request
import urllib.error

base = (input('Backend URL [http://127.0.0.1:8080]: ').strip() or 'http://127.0.0.1:8080').rstrip('/')
username = input('App username: ').strip()
password = getpass.getpass('App password: ')
token = None

def request(path, method='GET', data=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    payload = json.dumps(data).encode() if data is not None else None
    req = urllib.request.Request(base + path, data=payload, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as response:
        body = response.read()
        return json.loads(body) if body else None

try:
    result = request('/auth/login', 'POST', {'username': username, 'password': password})
    token = result['accessToken']
    password = None
    health = request('/health')
    assert health['phase'] == 8 and health['schemaVersion'] == 8, 'Expected Phase 8 backend/schema'
    for path in ['/customers?limit=5', '/suppliers?limit=5', '/receivingAccounts?limit=5', '/purchases?limit=5', '/inventory?limit=5', '/inventory?lowStock=true&limit=5', '/stockMovements?limit=5', '/packageStocks?limit=5', '/medicinePacks?limit=5', '/invoices?limit=5', '/payments?limit=5', '/customerInsights?limit=5', '/expenses?limit=5', '/notes?_limit=5']:
        rows = request(path)
        assert isinstance(rows, list), path + ' must return an array'
        print('PASS:', path, 'returned', len(rows), 'record(s).')
    assert isinstance(request('/dashboard')['chartPoints'], list)
    for kind in ['sales', 'purchases', 'stock', 'dues', 'expenses']:
        assert isinstance(request('/reports/' + kind + '?limit=5')['items'], list)
        print('PASS: report', kind)
    message_center = request('/messageCenter')
    assert message_center.get('sendingEnabled') is False, 'Message Center: sendingEnabled must be false'
    print('PASS: Message Center sending is disabled')
    catalog = request('/feedPriceCatalog')
    assert isinstance(catalog, list), 'Feed catalog response must be an array'
    assert len(catalog) == 76, f'Feed catalog: expected 76, received {len(catalog)}'
    print('PASS: feed catalog contains 76 entries')
    print('PASS: Phase 8 health and Phase 3 catalog preserved. No business records were created.')
except urllib.error.HTTPError as error:
    print('FAILED: HTTP', error.code)
    sys.exit(1)
except (AssertionError, OSError, KeyError, ValueError) as error:
    print('FAILED:', str(error))
    sys.exit(1)
finally:
    if token:
        try:
            request('/auth/logout', 'POST', {})
            print('Logged out.')
        except Exception:
            print('Logout could not be confirmed; the session may remain valid until expiry.')

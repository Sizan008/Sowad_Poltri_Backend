#!/usr/bin/env python3
"""Read-only catalog smoke checks; login/logout intentionally create audit events."""
import getpass
import json
import urllib.request
import urllib.error

base = input('Backend URL [http://127.0.0.1:8080]: ').strip() or 'http://127.0.0.1:8080'
base = base.rstrip('/')
username = input('App username: ').strip()
password = getpass.getpass('App password: ')
token = None

def request(path, method='GET', body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    payload = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data=payload, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=20) as response:
        raw = response.read()
        return json.loads(raw) if raw else None

try:
    result = request('/auth/login', 'POST', {'username': username, 'password': password})
    password = None
    token = result['accessToken']
    health = request('/health')
    assert health['phase'] == 7 and health['schemaVersion'] == 7, 'Wrong backend/schema version'
    categories = request('/categories')
    assert {c['kind'] for c in categories} == {'feed', 'medicine', 'chick', 'fish'}
    catalog = request('/feedPriceCatalog')
    assert len(catalog) == 76, 'Expected 76 reference entries'
    assert len({r['productCode'] for r in catalog}) == 76
    products = request('/products')
    assert len([r for r in products if r.get('feedCatalogId')]) == 76
    options = request('/catalog/cascade-options?category=feed&stage=groups')
    assert options['options'], 'Feed groups are empty'
    packs = request('/catalog/products/prod-nourish-3103/packaging')
    assert {5, 25, 50}.issubset({p['sizeBase'] for p in packs}), 'Bag definitions missing'
    assert len(request('/units')) >= 7
    print('PASS: health, 4 categories, 76 feed references, products, units, feed cascade and packaging.')
except urllib.error.HTTPError as error:
    print('FAILED: HTTP', error.code)
    raise SystemExit(1)
finally:
    if token:
        try:
            request('/auth/logout', 'POST', {})
            print('Logged out.')
        except Exception:
            print('Logout could not be confirmed; session may remain valid until expiry.')

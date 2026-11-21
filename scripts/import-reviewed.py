#!/usr/bin/env python3
"""Apply an explicitly reviewed master plan, one atomic record at a time. Never replays demo transactions."""
import argparse,getpass,json,re,urllib.request,urllib.error
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('plan',type=Path);p.add_argument('--api',default='http://127.0.0.1:8080');p.add_argument('--apply',action='store_true');a=p.parse_args()
plan=json.loads(a.plan.read_text());batch=plan['sourceSha256'];records=plan['records']
if not re.fullmatch('[0-9a-f]{64}',batch):raise SystemExit('Invalid source SHA-256')
allowed={'customers','suppliers','products','archivedProducts','units','receivingAccounts','categories','feedPriceCatalog'}
for r in records:
 if r['collection'] not in allowed or type(r['ordinal']) is not int or r['ordinal']<0 or r['action'] not in ['promote','link'] or not isinstance(r['command'],dict):raise SystemExit('Invalid plan record')
if len({(r['collection'],r['ordinal']) for r in records})!=len(records):raise SystemExit('Duplicate source record in plan')
print(f'Plan: {len(records)} master records; source {batch}. Transactions/stock/prices are not imported.')
if not a.apply:print('Review the plan and validation report; add --apply to import.');raise SystemExit(0)
username=input('Admin username: ');password=getpass.getpass('Admin password: ');token=None
# Refuse insecure remote HTTP; localhost HTTP is intended for the supplied run guide.
from urllib.parse import urlparse
url=urlparse(a.api)
if url.scheme not in ['http','https'] or (url.scheme=='http' and url.hostname not in ['127.0.0.1','localhost','::1']):raise SystemExit('Use HTTPS for a remote API')
def call(path,body):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(a.api.rstrip('/')+path,json.dumps(body).encode(),headers,method='POST')
 with urllib.request.urlopen(req,timeout=30) as response:
  data=response.read();return json.loads(data) if data else None
try:
 auth=call('/auth/login',{'username':username,'password':password});token=auth['accessToken'];password=None
 if auth['user']['role']!='admin':raise SystemExit('Admin role required')
 for r in records:
  result=call(f"/migration/{batch}/{r['collection']}/{r['ordinal']}/{r['action']}",r['command'])
  print(r['collection'],r['ordinal'],'->',result['id'])
 print('Completed. Query migration_mapping for preserved old/new ID relationships.')
except urllib.error.HTTPError as e:
 print('STOPPED: HTTP',e.code,e.read().decode()[:800]);raise SystemExit('Earlier successful records remain committed. Correct the rejected record and replay this plan; identical mappings are idempotent.')
finally:
 if token:
  try:call('/auth/logout',{})
  except Exception:print('Logout could not be confirmed; session expires normally.')

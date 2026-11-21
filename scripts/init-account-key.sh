#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import os,secrets,base64
from pathlib import Path
folder=Path.home()/'.config/nourish'
folder.mkdir(parents=True,exist_ok=True)
if folder.is_symlink():raise SystemExit('Refusing a symlink key directory.')
os.chmod(folder,0o700)
p=folder/'account-data.key'
try:fd=os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
except FileExistsError:
 print('Key file already exists; kept unchanged. Do not replace it after migration.')
else:
 with os.fdopen(fd,'w') as f:f.write(base64.b64encode(secrets.token_bytes(32)).decode()+'\n')
 print('Created owner-only key file. Back it up securely before migrating.')
print('Key file:',p)
PY

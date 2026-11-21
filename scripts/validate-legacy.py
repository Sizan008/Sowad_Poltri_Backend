#!/usr/bin/env python3
"""Offline, deterministic JSON preflight. Does not connect to a database or import live data."""
import argparse, collections, hashlib, json, re, subprocess
from decimal import Decimal, InvalidOperation
from pathlib import Path

def unique_object(pairs):
 d={}
 for k,v in pairs:
  if k in d: raise ValueError('Duplicate JSON object key: '+k)
  d[k]=v
 return d

def money(v):
 if isinstance(v,bool) or not isinstance(v,(int,float)): raise ValueError('Expected JSON number')
 d=Decimal(str(v))
 if not d.is_finite(): raise ValueError('Non-finite number')
 return d

def validate(data):
 if not isinstance(data,dict): raise ValueError('Root must be an object of collections')
 issues=[]; per=collections.defaultdict(list)
 def add(c,i,code,detail):
  entry={'collection':c,'ordinal':i,'id':data[c][i].get('id') if isinstance(data[c][i],dict) else None,'code':code,'detail':detail}
  issues.append(entry);per[(c,i)].append(entry)
 indexes={}
 for c,rows in data.items():
  if not isinstance(rows,list): raise ValueError('Collection must be an array: '+c)
  indexes[c]={}
  counts=collections.Counter(str(r.get('id')) for r in rows if isinstance(r,dict))
  for i,r in enumerate(rows):
   if not isinstance(r,dict): add(c,i,'NOT_OBJECT','Record is not an object');continue
   if not isinstance(r.get('id'),str) or not r['id']: add(c,i,'MISSING_ID','A stable string ID is required')
   elif counts[r['id']]>1: add(c,i,'DUPLICATE_ID','ID is not unique in this collection')
   else:indexes[c][r['id']]=r
   for field in ['quantity','stockQuantity','amount','totalAmount','unitPrice','lineTotal','bagCount','packCount','bagSizeKg','sizeBase']:
    if field in r:
     try:
      n=money(r[field])
      if n<0: add(c,i,'NEGATIVE_VALUE',field)
      if field in ['bagCount','packCount'] and n!=n.to_integral_value():add(c,i,'FRACTIONAL_PACKAGE',field)
     except (ValueError,InvalidOperation):add(c,i,'INVALID_NUMBER',field)
 for c in ['customers','suppliers']:
  phones=collections.Counter(str(r.get('phone','')).replace('+880','0').replace(' ','') for r in data.get(c,[]) if isinstance(r,dict))
  for i,r in enumerate(data.get(c,[])):
   if not isinstance(r,dict):continue
   for f in ['nameEn','addressEn']:
    value=r.get(f,'')
    if not isinstance(value,str) or len(value.strip())<2 or not re.fullmatch(r'[ -~]+',value) or not re.search('[A-Za-z]',value):add(c,i,'REQUIRED_ENGLISH_FIELD',f+' needs an explicit user-supplied value; no translation guessed')
   phone=str(r.get('phone','')).replace('+880','0').replace(' ','')
   if not re.fullmatch(r'01[3-9][0-9]{8}',phone):add(c,i,'INVALID_PHONE','BD mobile required')
   if phones[phone]>1:add(c,i,'DUPLICATE_PHONE','Canonical phone conflicts within source')
 refs={'products':{'categoryId':'categories'},'purchases':{'supplierId':'suppliers'},'purchaseItems':{'purchaseId':'purchases','productId':'products'},'invoices':{'customerId':'customers'},'invoiceItems':{'invoiceId':'invoices','productId':'products'},'payments':{'invoiceId':'invoices','customerId':'customers'},'packageStocks':{'productId':'products'},'medicinePacks':{'productId':'products'},'stockMovements':{'productId':'products'}}
 for c,fields in refs.items():
  for i,r in enumerate(data.get(c,[])):
   if not isinstance(r,dict):continue
   for f,target in fields.items():
    if r.get(f) not in indexes.get(target,{}):add(c,i,'MISSING_REFERENCE',f+' -> '+target+': '+str(r.get(f)))
 for c,items,ref in [('invoices','invoiceItems','invoiceId'),('purchases','purchaseItems','purchaseId')]:
  for i,r in enumerate(data.get(c,[])):
   if not isinstance(r,dict):continue
   lines=[x for x in data.get(items,[]) if isinstance(x,dict) and x.get(ref)==r.get('id')]
   if not lines:add(c,i,'MISSING_ITEMS','No item rows; transaction cannot be imported')
   else:
    try:
     total=sum((money(x['lineTotal']) for x in lines),Decimal(0));expected=money(r['totalAmount'])+money(r.get('discountAmount',0))
     if total!=expected:add(c,i,'TOTAL_MISMATCH',f'item sum {total}; header before discount {expected}')
    except (ValueError,KeyError,InvalidOperation):add(c,i,'INVALID_TOTAL','Total or line total missing/invalid')
   add(c,i,'DEMO_TRANSACTION_QUARANTINE','Requires complete historical snapshots, opening stock and independent reconciliation; never auto-replayed')
 for i,p in enumerate(data.get('payments',[])):
  if not isinstance(p,dict):continue
  inv=indexes.get('invoices',{}).get(p.get('invoiceId'))
  if inv and inv.get('customerId')!=p.get('customerId'):add('payments',i,'CUSTOMER_MISMATCH','Payment customer differs from invoice')
  if p.get('method')!='cash' and not p.get('receivingAccountSnapshot'):add('payments',i,'MISSING_ACCOUNT_SNAPSHOT','Historical receiving account evidence missing')
 for i,inv in enumerate(data.get('invoices',[])):
  if not isinstance(inv,dict):continue
  try:
   paid=sum((money(p['amount']) for p in data.get('payments',[]) if p.get('invoiceId')==inv.get('id')),Decimal(0))
   if paid>money(inv['totalAmount']):add('invoices',i,'OVERPAYMENT',str(paid))
  except (ValueError,KeyError,InvalidOperation):add('invoices',i,'INVALID_PAYMENT_TOTAL','Cannot reconcile payment')
 for i,p in enumerate(data.get('products',[])):
  if not isinstance(p,dict):continue
  if p.get('packagingStatus') in ['demo-allocation-unverified','legacy-unit-needs-reconciliation']:add('products',i,'UNVERIFIED_STOCK','Physical recount required; opening stock will not be imported')
  try:
   if p.get('kind')=='feed':qty=sum((money(x['bagSizeKg'])*money(x['bagCount']) for x in data.get('packageStocks',[]) if x.get('productId')==p.get('id')),Decimal(0))
   elif p.get('kind')=='medicine':qty=sum((money(x['sizeBase'])*money(x['packCount']) for x in data.get('medicinePacks',[]) if x.get('productId')==p.get('id')),Decimal(0))
   else:continue
   if qty!=money(p.get('stockQuantity',0)):add('products',i,'PACK_STOCK_MISMATCH',f'pack sum {qty}; product stock {p.get("stockQuantity")}')
  except (ValueError,KeyError,InvalidOperation):add('products',i,'INVALID_PACKAGE_STOCK','Cannot reconcile package quantity')
 return issues,per

def literal(value):return "'"+value.replace("'","''")+"'"
def main():
 ap=argparse.ArgumentParser();ap.add_argument('source',type=Path);ap.add_argument('--out',type=Path,required=True);args=ap.parse_args()
 raw=args.source.read_bytes();data=json.loads(raw,object_pairs_hook=unique_object,parse_constant=lambda x: (_ for _ in ()).throw(ValueError('Invalid numeric constant')))
 issues,per=validate(data);digest=hashlib.sha256(raw).hexdigest();args.out.mkdir(parents=True,exist_ok=True)
 report={'sourceSha256':digest,'counts':{k:len(v) for k,v in data.items()},'issueCount':len(issues),'byCode':dict(collections.Counter(x['code'] for x in issues)),'issues':issues,'decision':'STAGING ONLY. No record is approved for live import by this report. Master promotion revalidates explicit commands on the server. Demo transactions remain quarantined.'}
 (args.out/'validation-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 lines=['# Legacy JSON validation','',f'Source SHA-256: `{digest}`','',report['decision'],'','## Collection counts','']+[f'- {k}: {len(v)}' for k,v in data.items()]+['','## Findings','']+[f'- {k}: {v}' for k,v in report['byCode'].items()]+['','See validation-report.json for every record and reason. Original archive flags, reasons, IDs and references are preserved in migration_record.payload. User credentials are excluded.']
 (args.out/'validation-report.md').write_text('\n'.join(lines)+'\n')
 j=lambda x:json.dumps(x,ensure_ascii=False,separators=(',',':'))
 # Encrypt all known account-number fields before producing database staging SQL.
 # The original validation source and local report remain sensitive files.
 bundle={'report':report,'data':data,'recordIssues':{c:[per[(c,i)] for i in range(len(rows))] for c,rows in data.items()}}
 helper=Path(__file__).resolve().parent/'account-encryption.sh'
 secured=subprocess.run(['bash',str(helper),'protect-json'],input=j(bundle),capture_output=True,text=True)
 if secured.returncode:raise RuntimeError('Cannot protect staging SQL. Build the patched backend and set ACCOUNT_DATA_KEY first.')
 bundle=json.loads(secured.stdout);report=bundle['report'];data=bundle['data']
 per={(c,i):v for c,rows in bundle['recordIssues'].items() for i,v in enumerate(rows)}
 # Values are SQL literals with standard_conforming_strings enabled. No identifiers come from JSON.
 sql=['BEGIN;','SET LOCAL standard_conforming_strings=on;',"DO $$ BEGIN IF current_database()<>'nourish_poltri_shop' OR NOT EXISTS(SELECT 1 FROM nourish.schema_version WHERE version=8) THEN RAISE EXCEPTION 'Apply Phase 8 in nourish_poltri_shop first'; END IF; END $$;",'INSERT INTO nourish.migration_batch(source_sha256,source_name,report) VALUES('+','.join([literal(digest),literal(args.source.name),literal(j(report))+'::jsonb'])+') ON CONFLICT DO NOTHING;']
 for c,rows in data.items():
  if c=='users':continue
  for i,row in enumerate(rows):
   legacy=row.get('id') if isinstance(row,dict) else None
   sql.append('INSERT INTO nourish.migration_record(source_sha256,collection,ordinal,legacy_id,payload,issues) VALUES('+','.join([literal(digest),literal(c),str(i),'NULL' if legacy is None else literal(str(legacy)),literal(j(row))+'::jsonb',literal(j(per[(c,i)]))+'::jsonb'])+') ON CONFLICT DO NOTHING;')
 sql+=['COMMIT;','SELECT collection,count(*) FROM nourish.migration_record WHERE source_sha256='+literal(digest)+' GROUP BY collection ORDER BY collection;']
 (args.out/'legacy-staging.sql').write_text('\n'.join(sql)+'\n')
 print(json.dumps({'sourceSha256':digest,'issues':len(issues),'byCode':report['byCode'],'liveRecordsImported':0}))
if __name__=='__main__':main()

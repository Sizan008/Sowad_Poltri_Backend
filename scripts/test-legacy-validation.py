import importlib.util,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('legacy',Path(__file__).with_name('validate-legacy.py'));m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class PreflightTest(unittest.TestCase):
 def codes(self,data):return {i['code'] for i in m.validate(data)[0]}
 def test_missing_items_and_reference(self):
  self.assertTrue({'MISSING_ITEMS','MISSING_REFERENCE'}<=self.codes({'invoices':[{'id':'i','customerId':'gone','totalAmount':2}]}))
 def test_mismatch_and_overpayment(self):
  c=self.codes({'customers':[{'id':'c','nameEn':'Buyer','addressEn':'Dhaka Road','phone':'01712345678'}],'products':[{'id':'p','categoryId':'cat','kind':'fish'}],'categories':[{'id':'cat'}],'invoices':[{'id':'i','customerId':'c','totalAmount':10}],'invoiceItems':[{'id':'l','invoiceId':'i','productId':'p','lineTotal':9}],'payments':[{'id':'pay','invoiceId':'i','customerId':'c','amount':11,'method':'cash'}]})
  self.assertTrue({'TOTAL_MISMATCH','OVERPAYMENT'}<=c)
 def test_duplicate_and_fractional_package(self):
  self.assertTrue({'DUPLICATE_ID','FRACTIONAL_PACKAGE'}<=self.codes({'packageStocks':[{'id':'x','bagCount':1.5},{'id':'x','bagCount':1}]}))
 def test_duplicate_json_keys_rejected(self):
  with self.assertRaises(ValueError):m.unique_object([('id','a'),('id','b')])
 def test_quote_escaping(self):self.assertEqual(m.literal("O'Brien"),"'O''Brien'")
if __name__=='__main__':unittest.main()

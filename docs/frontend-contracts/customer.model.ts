import { CustomerRate } from './customer-rate.model';

/** Raw database text is never machine-translated when UI language changes. */
export interface Customer {
  id: string;
  name: string;
  nameEn?: string;
  nameBn?: string;
  phone: string;
  whatsappPhone?: string;
  area: string;
  addressEn?: string;
  addressBn?: string;
  lastNote?: string;
  customerRates?: CustomerRate[];
  status: 'active' | 'inactive';
  createdAt: string;
}

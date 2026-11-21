export interface Supplier {
  id: string;
  name: string;
  nameEn?: string;
  nameBn?: string;
  phone: string;
  area: string;
  addressEn?: string;
  addressBn?: string;
  whatsappPhone?: string;
  status?: 'active' | 'inactive';
  createdAt?: string;
}

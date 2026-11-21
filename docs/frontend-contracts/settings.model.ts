export interface BusinessSettings {
  id: 'business';
  businessName: string;
  ownerName: string;
  phone: string;
  email: string;
  address: string;
  invoiceFooter: string;
  lowStockDefault: number;
  currency: 'BDT';
  uiLanguage: 'bn' | 'en';
}

export type UpdateBusinessSettingsRequest = Omit<BusinessSettings, 'id' | 'currency'>;

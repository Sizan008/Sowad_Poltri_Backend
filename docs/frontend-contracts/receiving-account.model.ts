/** Business-owned receiving destination; demo JSON Server is NOT secure storage. */
export type PaymentChannel = 'cash' | 'bkash' | 'nagad' | 'bank';
export type AccountKind = 'mfs' | 'bank';
export type MfsProvider = 'bkash' | 'nagad';

export interface ReceivingAccount {
  id: string;
  kind: AccountKind;
  label: string;
  active: boolean;
  provider?: MfsProvider;
  walletNumber?: string;
  bankName?: string;
  accountNumber?: string;
  accountName?: string;
}

export type SaveReceivingAccount = Omit<ReceivingAccount, 'id'>;

export interface PaymentAccountSnapshot {
  kind: AccountKind;
  label: string;
  provider?: MfsProvider;
  walletNumber?: string;
  bankName?: string;
  accountNumber?: string;
  accountName?: string;
}

export interface PaymentDestination {
  paymentProvider?: 'bkash' | 'nagad' | 'bank';
  receivingAccountId?: string;
  receivingAccountSnapshot?: PaymentAccountSnapshot;
  senderNumber?: string;
  transactionRef?: string;
}

export function paymentMethodFor(channel: string): 'cash' | 'bank' | 'mobile-banking' {
  if (channel === 'bank') return 'bank';
  if (channel === 'bkash' || channel === 'nagad') return 'mobile-banking';
  return 'cash';
}

/** Shared request shaping for initial payments and later collections. */
export function paymentDestinationFor(
  channel: string, accountId: string, sender: string, reference: string,
): PaymentDestination {
  if (channel === 'cash') return {};
  return {
    paymentProvider: channel === 'bkash' || channel === 'nagad' ? channel : 'bank',
    receivingAccountId: accountId,
    senderNumber: sender.trim(),
    transactionRef: reference.trim(),
  };
}

export function paymentDestinationLabel(payment: PaymentDestination): string {
  const snapshot = payment.receivingAccountSnapshot;
  if (!snapshot) return payment.receivingAccountId ? 'Account not found' : '—';
  if (snapshot.kind === 'bank') {
    return `${snapshot.bankName ?? ''} • ${snapshot.accountNumber ?? ''}`;
  }
  return `${snapshot.provider === 'bkash' ? 'bKash' : 'Nagad'} • ${snapshot.walletNumber ?? ''}`;
}

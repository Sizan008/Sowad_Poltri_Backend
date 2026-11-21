/** Demonstration accounts only. No real security boundary exists. */
export type DemoRole = 'admin' | 'operator' | 'viewer';

export interface DemoUser {
  id: string;
  username: string;
  displayName: string;
  role: DemoRole;
}

export interface DemoAccount extends DemoUser {
  password: string;
}

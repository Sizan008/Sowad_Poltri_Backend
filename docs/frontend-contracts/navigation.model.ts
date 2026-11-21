import { DemoRole } from './auth.model';

export interface NavigationItem {
  title: string;
  path: string;
  icon: string;
  phase: 1 | 2 | 3 | 4;
  roles?: readonly DemoRole[];
}

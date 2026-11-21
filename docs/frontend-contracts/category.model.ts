export interface Category {
  id: string;
  name: string;
  description?: string;
  kind?: 'feed' | 'medicine' | 'chick' | 'fish';
  archived?: boolean;
}

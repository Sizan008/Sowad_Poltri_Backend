/** Persisted local JSON Server note. Serial is monotonically assigned by backend. */
export interface NotebookNote {
  id: string;
  serial: number;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
  authorId: string;
  authorName: string;
}
export interface SaveNotebookNote { title: string; content: string; }
export interface NotePage { items: NotebookNote[]; total: number; }

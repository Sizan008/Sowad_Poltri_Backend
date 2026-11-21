/** Only local demo audit; fields are supplied by an untrusted client. */
export interface ActivityLogEntry {
  id: string;
  actorId: string;
  actorName: string;
  role: string;
  action: string;
  entity: string;
  entityId: string | null;
  details: string;
  occurredAt: string;
}

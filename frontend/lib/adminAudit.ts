export type AuditEvent = {
  id: string; occurredAt: string; eventType: string; actorType: string; actorId: string;
  correlationId: string; entityType: string; entityId: string; outcome: string;
  metadata: Record<string, string | number | boolean | null>; reason?: string; retentionUntil: string;
};
export type AuditPage = { items: AuditEvent[]; page: number; size: number; total: number };
export type AuditFilters = {
  eventType: string; actorId: string; correlationId: string; entityType: string; entityId: string;
  outcome: string; occurredFrom: string; occurredTo: string;
};
export type AuditRetention = { retentionDays: number; updatedBy: string; updatedAt: string };

export const emptyAuditFilters = (): AuditFilters => ({ eventType: "", actorId: "", correlationId: "",
  entityType: "", entityId: "", outcome: "", occurredFrom: "", occurredTo: "" });

function params(filters: AuditFilters, page?: number, size?: number) {
  const result = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) result.set(key, key.startsWith("occurred") ? new Date(value).toISOString() : value);
  });
  if (page !== undefined) result.set("page", String(page));
  if (size !== undefined) result.set("size", String(size));
  return result;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/backend/admin/audit/${path}`, { cache: "no-store", ...init });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Operazione audit non riuscita (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function fetchAuditEvents(filters: AuditFilters, page = 0, size = 20) {
  return request<AuditPage>(`events?${params(filters, page, size)}`);
}
export function auditExportUrl(filters: AuditFilters) { return `/api/backend/admin/audit/events/export?${params(filters)}`; }
export function fetchReportAuditTimeline(reportId: string) { return request<AuditEvent[]>(`reports/${reportId}/timeline`); }
export function fetchAuditRetention() { return request<AuditRetention>("retention"); }
export function saveAuditRetention(retentionDays: number) {
  return request<AuditRetention>("retention", { method: "PUT", headers: { "content-type": "application/json" }, body: JSON.stringify({ retentionDays }) });
}
export function applyAuditRetention() { return request<{ deletedEvents: number; appliedAt: string }>("retention/apply", { method: "POST" }); }

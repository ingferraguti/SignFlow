export type DeliveryChannel = "FSE" | "CONSERVATION";
export type DeliveryState = "FSE_VALIDATION_ERROR" | "FSE_SENT" | "FSE_ACCEPTED" | "FSE_REJECTED" |
  "CONSERVATION_SENT" | "CONSERVATION_ACCEPTED" | "CONSERVATION_REJECTED" | "TIMEOUT";
export type DeliveryOperation = {
  id: string; reportId: string; reportIdentifier: string; documentId?: string; channel: DeliveryChannel;
  state: DeliveryState; correlationId: string; adapterCode: string; facilityCode: string; facilityName: string;
  operatingUnit: string; department: string; documentTypeCode: string; remoteReference?: string;
  attemptCount: number; maxRetries: number; version: number; reportVersion: number; errorCode?: string;
  errorMessage?: string; createdBy: string; createdAt: string; sentAt?: string; completedAt?: string;
  reconciledAt?: string; updatedAt: string;
};
export type DeliveryAttempt = { id: string; attemptNumber: number; action: string; outcome: string;
  correlationId: string; remoteReference?: string; errorCode?: string; errorMessage?: string;
  startedAt: string; completedAt: string };
export type DeliveryReceipt = { id: string; attemptId: string; receiptType: string; mimeType: string;
  sizeBytes: number; sha256: string; createdAt: string; downloadUrl: string };
export type DeliveryDetail = { operation: DeliveryOperation; metadata: Record<string, string>;
  attempts: DeliveryAttempt[]; receipts: DeliveryReceipt[]; retryAllowed: boolean; reconciliationAllowed: boolean };
export type DeliveryPage = { items: DeliveryOperation[]; page: number; size: number; total: number };
export type DeliveryFilters = { channel: string; state: string; correlationId: string; reportIdentifier: string };
export const emptyDeliveryFilters = (): DeliveryFilters => ({ channel: "", state: "", correlationId: "", reportIdentifier: "" });

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/backend/admin/external-deliveries${path}`, { cache: "no-store", ...init });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Operazione FSE/conservazione non riuscita (${response.status})`);
  }
  return response.json() as Promise<T>;
}
export function fetchDeliveries(filters: DeliveryFilters, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  return request<DeliveryPage>(`?${params}`);
}
export function fetchDelivery(id: string) { return request<DeliveryDetail>(`/${id}`); }
export function startDelivery(channel: DeliveryChannel, reportId: string, expectedVersion: number) {
  const path = channel === "FSE" ? `/fse/reports/${reportId}` : `/conservation/reports/${reportId}`;
  return request<DeliveryDetail>(path, { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ expectedVersion, operationKey: crypto.randomUUID() }) });
}
export function retryDelivery(operation: DeliveryOperation) {
  return request<DeliveryDetail>(`/${operation.id}/retry`, { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ expectedVersion: operation.reportVersion, operationKey: crypto.randomUUID() }) });
}
export function reconcileDelivery(operation: DeliveryOperation) {
  return request<DeliveryDetail>(`/${operation.id}/reconcile`, { method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ expectedVersion: operation.reportVersion, operationKey: crypto.randomUUID() }) });
}

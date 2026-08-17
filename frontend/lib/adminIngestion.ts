export type IngestionMessage = {
  id: string; receivedAt: string; processedAt?: string; transport: "REST" | "MLLP";
  status: "RECEIVED" | "PROCESSED" | "DISCARDED"; sourceSystemCode?: string;
  messageType?: string; triggerEvent?: string; controlId?: string; correlationId: string;
  reportId?: string; documentId?: string; errorCode?: string; errorMessage?: string;
};
export type IngestionMessagePage = { items: IngestionMessage[]; page: number; size: number; total: number };
export type IngestionMessageDetail = {
  message: IngestionMessage; hl7Version?: string; payloadSha256: string; rawSizeBytes: number;
  rawRetentionUntil: string; cdaBuilderCalled: boolean; documentNormalizerCalled: boolean;
  pdfA3ConverterCalled: boolean; passthroughApplied: boolean; pipelineSteps: string[];
  duplicateOfId?: string; maskedRawPreview: string;
};
export type IngestionFilters = { status: string; sourceSystemCode: string; messageType: string; correlationId: string; receivedFrom: string; receivedTo: string };
export type MissingSignerReport = { reportId: string; internalIdentifier: string; sourceSystemCode: string; documentType: string; department: string; producedAt: string };
export type MissingSignerPage = { items: MissingSignerReport[]; page: number; size: number; total: number };
export const emptyIngestionFilters = (): IngestionFilters => ({ status: "", sourceSystemCode: "", messageType: "", correlationId: "", receivedFrom: "", receivedTo: "" });

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`/api/backend/admin/monitoring/${path}`, { cache: "no-store" });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Monitoraggio ingestion non disponibile (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function fetchIngestionMessages(filters: IngestionFilters, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  Object.entries(filters).forEach(([key, value]) => {
    if (value) params.set(key, key.startsWith("received") ? new Date(value).toISOString() : value);
  });
  return request<IngestionMessagePage>(`messages?${params}`);
}
export function fetchIngestionMessage(id: string) { return request<IngestionMessageDetail>(`messages/${id}`); }
export function fetchMissingSignerReports(page = 0, size = 20) { return request<MissingSignerPage>(`reports-without-signer?page=${page}&size=${size}`); }

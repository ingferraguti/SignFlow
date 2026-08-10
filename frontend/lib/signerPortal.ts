import type { ClinicalDocument } from "./adminDocuments";
import type { ReportDetail, ReportPage, ReportState, ReportSummary } from "./adminReports";

export type SignerFilters = {
  query: string; patient: string; documentType: string; department: string; state: string;
  producedFrom: string; producedTo: string; signedFrom: string; signedTo: string;
};
export type SignerHome = {
  total: number; readyToSign: number; reviewPending: number; incomplete: number; signed: number;
  recentReports: ReportSummary[];
};
export type SignerProfile = {
  id: string; username: string; firstName: string; lastName: string; email?: string; signerFiscalCode?: string;
  partitionCode: string; partitionName: string; companyCode: string; companyName: string; groups: string[];
};
export type StateLegend = { code: ReportState; label: string; description: string };
export type Preview = { url: string; expiresAt: string; reportState: ReportState; workflowVersion: number; firstPreviewedAt?: string };

export class PortalApiError extends Error {
  constructor(message: string, readonly status: number) { super(message); }
  get sessionExpired() { return this.status === 401; }
}

const base = "/api/backend/signer";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}/${path}`, { cache: "no-store", ...init });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    const message = response.status === 401 ? "La sessione è scaduta. Accedi nuovamente."
      : body.message ?? `Servizio firmatario non disponibile (${response.status})`;
    throw new PortalApiError(message, response.status);
  }
  return response.json() as Promise<T>;
}

export const emptySignerFilters = (): SignerFilters => ({ query: "", patient: "", documentType: "", department: "",
  state: "", producedFrom: "", producedTo: "", signedFrom: "", signedTo: "" });
export const fetchSignerHome = () => request<SignerHome>("home");
export const fetchSignerProfile = () => request<SignerProfile>("profile");
export const fetchStateLegend = () => request<StateLegend[]>("states");
export const fetchSignerReport = (id: string) => request<ReportDetail>(`reports/${id}`);
export const fetchSignerDocuments = (id: string) => request<ClinicalDocument[]>(`reports/${id}/documents`);
export const previewSignerDocument = (reportId: string, documentId: string, expectedVersion: number, operationKey: string) =>
  request<Preview>(`reports/${reportId}/documents/${documentId}/preview`, {
    method: "POST", headers: { "content-type": "application/json" },
    body: JSON.stringify({ expectedVersion, operationKey }),
  });

export function fetchSignerReports(filters: SignerFilters, page = 0, size = 20): Promise<ReportPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  return request<ReportPage>(`reports?${params}`);
}

export async function downloadSignerDocument(reportId: string, document: ClinicalDocument) {
  const response = await fetch(`${base}/reports/${reportId}/documents/${document.id}/content`, { cache: "no-store" });
  if (!response.ok) throw new PortalApiError(response.status === 401 ? "La sessione è scaduta. Accedi nuovamente." : "Documento non disponibile.", response.status);
  const url = URL.createObjectURL(await response.blob());
  const anchor = window.document.createElement("a"); anchor.href = url; anchor.download = document.originalFilename; anchor.click();
  URL.revokeObjectURL(url);
}

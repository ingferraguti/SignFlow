export const reportStates = [
  "RECEIVED", "PARSED", "INCOMPLETE", "MISSING_SIGNER", "READY_TO_SIGN", "PREVIEWED",
  "REVIEW_PENDING", "APPROVED", "SIGN_BATCH_CREATED", "SIGNING", "SIGNED", "SIGN_ERROR",
  "FSE_VALIDATION_ERROR", "FSE_SENT", "FSE_ACCEPTED", "FSE_REJECTED",
  "CONSERVATION_SENT", "CONSERVATION_ACCEPTED",
] as const;
export type ReportState = typeof reportStates[number];

export type ReportFilters = {
  internalIdentifier: string; externalIdentifier: string; fseIdentifier: string;
  patient: string; signer: string; signerFiscalCode: string; state: string;
  sourceSystemId: string; department: string; producedFrom: string; producedTo: string;
  modifiedFrom: string; modifiedTo: string; signedFrom: string; signedTo: string;
  sortBy: string; direction: string;
};
export type ReportSummary = {
  id: string; internalIdentifier: string; externalIdentifier?: string; fseIdentifier?: string;
  practiceIdentifier: string; patientIdentifier: string; patientDisplayName: string;
  assignedSignerId?: string; signerUsername?: string; signerFiscalCode?: string;
  sourceSystemId: string; sourceSystemCode: string; documentType: string; department: string;
  producedAt: string; modifiedAt: string; signedAt?: string; state: ReportState;
};
export type ReportDetail = {
  id: string; internalIdentifier: string; externalIdentifier?: string; fseIdentifier?: string;
  practice: { id: string; practiceIdentifier: string; externalReference?: string; description: string };
  patient: { id: string; patientIdentifier: string; firstName: string; lastName: string; fiscalCode?: string; birthDate?: string };
  assignedSignerId?: string; signerUsername?: string; signerFiscalCode?: string;
  sourceSystemId: string; sourceSystemCode: string; documentType: string; department: string;
  producedAt: string; modifiedAt: string; signedAt?: string; state: ReportState;
  pdfA3Conversion: boolean; visibleSignature: boolean; multipleSignature: boolean;
  sendUnsigned: boolean; createCda: boolean; passthrough: boolean;
};
export type ReportPage = { items: ReportSummary[]; page: number; size: number; total: number };
export type SourceSystemOption = { id: string; code: string; description: string };

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`/api/backend/admin/${path}`, { cache: "no-store" });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Ricerca referti non riuscita (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export function emptyReportFilters(): ReportFilters {
  return { internalIdentifier: "", externalIdentifier: "", fseIdentifier: "", patient: "", signer: "",
    signerFiscalCode: "", state: "", sourceSystemId: "", department: "", producedFrom: "", producedTo: "",
    modifiedFrom: "", modifiedTo: "", signedFrom: "", signedTo: "", sortBy: "producedAt", direction: "desc" };
}

export function fetchReports(filters: ReportFilters, page = 0, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size), sortBy: filters.sortBy, direction: filters.direction });
  Object.entries(filters).forEach(([key, value]) => { if (value && key !== "sortBy" && key !== "direction") params.set(key, value); });
  return request<ReportPage>(`reports?${params}`);
}

export function fetchReportDetail(id: string) { return request<ReportDetail>(`reports/${id}`); }
export function fetchReportSourceSystems() { return request<SourceSystemOption[]>("technical-config/source-systems"); }

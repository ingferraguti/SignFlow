import type { ClinicalDocument } from "./adminDocuments";
import type { ReportDetail, ReportReview, ReviewOperationResult } from "./adminReports";

export type ApproverQueueItem = { id: string; internalIdentifier: string; patientName: string; documentType: string; department: string; producedAt: string; state: string; workflowVersion: number };
export type ReviewPreview = { url: string; expiresAt: string; state: string; workflowVersion: number; review: ReportReview };
const base = "/api/backend/approver";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}/${path}`, { cache: "no-store", ...init });
  if (!response.ok) { const body = await response.json().catch(() => ({})) as { message?: string }; throw new Error(body.message ?? `Servizio approvatore non disponibile (${response.status})`); }
  return response.json() as Promise<T>;
}
const post = <T>(path: string, body: object) => request<T>(path, { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body) });
export const fetchApproverQueue = () => request<ApproverQueueItem[]>("reports");
export const fetchApproverReport = (id: string) => request<ReportDetail>(`reports/${id}`);
export const fetchApproverReview = (id: string) => request<ReportReview>(`reports/${id}/review`);
export const fetchApproverDocuments = (id: string) => request<ClinicalDocument[]>(`reports/${id}/documents`);
export const openApproverDocument = (reportId: string, documentId: string, expectedVersion: number) => post<ReviewPreview>(`reports/${reportId}/documents/${documentId}/preview`, { expectedVersion, operationKey: crypto.randomUUID() });
export const approveReport = (reportId: string, expectedVersion: number) => post<ReviewOperationResult>(`reports/${reportId}/approve`, { expectedVersion, operationKey: crypto.randomUUID() });
export const rejectReport = (reportId: string, expectedVersion: number, reason: string) => post<ReviewOperationResult>(`reports/${reportId}/reject`, { expectedVersion, reason, operationKey: crypto.randomUUID() });

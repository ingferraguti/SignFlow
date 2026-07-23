export type ClinicalDocument = {
  id: string; reportId: string; sha256: string; mimeType: string; sizeBytes: number; version: number;
  originalFilename: string; objectIdentifier: string; uploadedBy: string; uploadedAt: string;
  status: "ACTIVE" | "DELETED"; deletedAt?: string; deletedBy?: string;
};

export type TemporaryDocumentUrl = { url: string; expiresAt: string };

const base = (reportId: string) => `/api/backend/admin/reports/${reportId}/documents`;

async function error(response: Response) {
  const body = await response.json().catch(() => ({})) as { message?: string };
  return new Error(body.message ?? `Operazione documento non riuscita (${response.status})`);
}

export async function fetchDocuments(reportId: string, includeDeleted = false) {
  const response = await fetch(`${base(reportId)}?includeDeleted=${includeDeleted}`, { cache: "no-store" });
  if (!response.ok) throw await error(response);
  return response.json() as Promise<ClinicalDocument[]>;
}

export async function uploadDocument(reportId: string, file: File) {
  const form = new FormData(); form.set("file", file);
  const response = await fetch(base(reportId), { method: "POST", body: form });
  if (!response.ok) throw await error(response);
  return response.json() as Promise<ClinicalDocument>;
}

export async function temporaryDocumentUrl(reportId: string, documentId: string, disposition = "inline") {
  const response = await fetch(`${base(reportId)}/${documentId}/temporary-url?disposition=${disposition}`, { cache: "no-store" });
  if (!response.ok) throw await error(response);
  return response.json() as Promise<TemporaryDocumentUrl>;
}

export async function downloadDocument(reportId: string, document: ClinicalDocument) {
  const response = await fetch(`${base(reportId)}/${document.id}/content?disposition=attachment`, { cache: "no-store" });
  if (!response.ok) throw await error(response);
  const url = URL.createObjectURL(await response.blob());
  const anchor = window.document.createElement("a");
  anchor.href = url; anchor.download = document.originalFilename; anchor.click();
  URL.revokeObjectURL(url);
}

export async function deleteDocument(reportId: string, documentId: string) {
  const response = await fetch(`${base(reportId)}/${documentId}`, { method: "DELETE" });
  if (!response.ok) throw await error(response);
}

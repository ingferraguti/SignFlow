export type ProviderSession = {
  id: string; providerCode: string; state: string; expiresAt: string; warning: string;
};
export type SignatureAttempt = {
  id: string; reportId: string; reportIdentifier: string;
  state: "PENDING" | "SIGNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  retryCount: number; maxRetries: number; providerReference?: string; artifactId?: string;
  artifactName?: string; artifactNotice?: string; errorCode?: string; errorMessage?: string;
  startedAt?: string; completedAt?: string;
};
export type SignatureBatch = {
  id: string; signerUsername: string; providerCode: string;
  selectionMode: "SINGLE" | "MANUAL" | "FILTERED"; filterSnapshot?: string;
  state: "DRAFT" | "CONFIRMED" | "RUNNING" | "COMPLETED" | "PARTIAL_SUCCESS" | "FAILED" | "CANCELLED";
  version: number; totalCount: number; successCount: number; failureCount: number;
  createdAt: string; confirmedAt?: string; startedAt?: string; completedAt?: string; cancelledAt?: string;
  warning: string; attempts: SignatureAttempt[];
};

const base = "/api/backend/signer/signatures";
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(base + "/" + path, { cache: "no-store", ...init });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? "Operazione di firma mock non riuscita (" + response.status + ")");
  }
  return response.json() as Promise<T>;
}
const post = <T>(path: string, body: object) => request<T>(path, {
  method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body),
});

export const openProviderSession = (authorizationCode: string) =>
  post<ProviderSession>("provider-sessions", { authorizationCode });
export const fetchSignatureBatches = () => request<SignatureBatch[]>("batches");
export const fetchSignatureBatch = (id: string) => request<SignatureBatch>("batches/" + id);
export const createManualBatch = (reportIds: string[]) => post<SignatureBatch>("batches", {
  selectionMode: "MANUAL", reportIds, operationKey: crypto.randomUUID(),
});
export const createFilteredBatch = (filters: { query: string; patient: string; documentType: string; department: string }) =>
  post<SignatureBatch>("batches", { selectionMode: "FILTERED", filters, operationKey: crypto.randomUUID() });
export const confirmSignatureBatch = (id: string, providerSessionId: string) =>
  post<SignatureBatch>("batches/" + id + "/confirm", { providerSessionId, operationKey: crypto.randomUUID() });
export const startSignatureBatch = (id: string, providerSessionId: string) =>
  post<SignatureBatch>("batches/" + id + "/start", { providerSessionId, operationKey: crypto.randomUUID() });
export const cancelSignatureBatch = (id: string) =>
  post<SignatureBatch>("batches/" + id + "/cancel", { operationKey: crypto.randomUUID() });
export const retrySignatureAttempt = (batchId: string, attemptId: string, providerSessionId: string) =>
  post<SignatureBatch>("batches/" + batchId + "/attempts/" + attemptId + "/retry",
    { providerSessionId, operationKey: crypto.randomUUID() });
export const signSingleMock = (reportId: string, providerSessionId: string) =>
  post<SignatureBatch>("single", { reportId, providerSessionId, operationKey: crypto.randomUUID() });

export async function downloadMockArtifact(artifactId: string, filename: string) {
  const response = await fetch(base + "/artifacts/" + artifactId, { cache: "no-store" });
  if (!response.ok) throw new Error("Attestazione mock non disponibile");
  const url = URL.createObjectURL(await response.blob());
  const anchor = document.createElement("a"); anchor.href = url; anchor.download = filename; anchor.click();
  URL.revokeObjectURL(url);
}

import type { ApplicationUser, OrganizationOptions } from "./adminUsers";

export type SourceSystem = {
  id: string; code: string; companyId: string; companyCode: string; description: string; active: boolean;
  cdaType: string; pdfA3Conversion: boolean; visibleSignature: boolean; multipleSignature: boolean;
  sendUnsigned: boolean; createCda: boolean; passthrough: boolean;
};
export type SourceSystemRequest = Omit<SourceSystem, "id" | "companyCode">;

export type SignatureAuthenticationMode = "NONE" | "OTP" | "USERNAME_OTP" | "OAUTH2" | "CERTIFICATE" | "API_KEY_REFERENCE";
export type SignatureProvider = {
  id: string; code: string; name: string; adapterType: string; baseUrl: string;
  authenticationMode: SignatureAuthenticationMode; credentialReference: string;
  supportsVisibleSignature: boolean; supportsMultipleSignature: boolean; active: boolean;
};
export type SignatureProviderRequest = Omit<SignatureProvider, "id">;

export type SignatureAccount = {
  id: string; applicationUserId: string; applicationUsername: string; signatureProviderId: string;
  naturalPersonId: string; signatureProviderCode: string; accountAlias: string; providerUsername: string;
  certificateAlias: string; displayName: string; signatureType: string; qualified: boolean; active: boolean;
};
export type SignatureAccountRequest = Omit<SignatureAccount,
  "id" | "applicationUsername" | "naturalPersonId" | "signatureProviderCode">;

export type FseFacilityMapping = {
  id: string; facilityCode: string; facilityName: string; companyId: string; companyCode: string;
  operatingUnit: string; department: string; sourceSystemId: string; sourceSystemCode: string; active: boolean;
};
export type FseFacilityMappingRequest = Omit<FseFacilityMapping, "id" | "companyCode" | "sourceSystemCode">;

export type FseDocumentType = { code: string; displayName: string; description: string; active: boolean; approvalRequired: boolean; previewRequired: boolean };
export type SourceSystemFseDocumentType = {
  sourceSystemId: string; sourceSystemCode: string; documentTypeCode: string;
  documentTypeName: string; cdaInjectionEnabled: boolean;
};

export type TechnicalConfigurationData = {
  sourceSystems: SourceSystem[]; signatureProviders: SignatureProvider[];
  signatureAccounts: SignatureAccount[]; fseFacilityMappings: FseFacilityMapping[];
  fseDocumentTypes: FseDocumentType[]; sourceSystemFseDocumentTypes: SourceSystemFseDocumentType[];
  organization: OrganizationOptions; users: ApplicationUser[];
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/backend/admin/${path}`, {
    ...init,
    headers: { "content-type": "application/json", ...(init?.headers ?? {}) },
    cache: "no-store",
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string };
    throw new Error(body.message ?? `Richiesta amministrativa non riuscita (${response.status})`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export async function fetchTechnicalConfiguration(): Promise<TechnicalConfigurationData> {
  const [sourceSystems, signatureProviders, signatureAccounts, fseFacilityMappings, fseDocumentTypes, organization, users] = await Promise.all([
    request<SourceSystem[]>("technical-config/source-systems"),
    request<SignatureProvider[]>("technical-config/signature-providers"),
    request<SignatureAccount[]>("technical-config/signature-accounts"),
    request<FseFacilityMapping[]>("technical-config/fse-facility-mappings"),
    request<FseDocumentType[]>("technical-config/fse-document-types"),
    import("./adminUsers").then(({ fetchOrganizationOptions }) => fetchOrganizationOptions()),
    import("./adminUsers").then(({ fetchUsers }) => fetchUsers("").then((page) => page.items)),
  ]);
  const sourceSystemFseDocumentTypes = (await Promise.all(sourceSystems.map((source) =>
    request<SourceSystemFseDocumentType[]>(`technical-config/source-systems/${source.id}/fse-document-types`)
  ))).flat();
  return { sourceSystems, signatureProviders, signatureAccounts, fseFacilityMappings, fseDocumentTypes, sourceSystemFseDocumentTypes, organization, users };
}

function save<TRequest, TResponse>(resource: string, value: TRequest, id?: string) {
  return request<TResponse>(`technical-config/${resource}${id ? `/${id}` : ""}`, {
    method: id ? "PUT" : "POST", body: JSON.stringify(value),
  });
}

export const saveSourceSystem = (value: SourceSystemRequest, id?: string) => save<SourceSystemRequest, SourceSystem>("source-systems", value, id);
export const saveSignatureProvider = (value: SignatureProviderRequest, id?: string) => save<SignatureProviderRequest, SignatureProvider>("signature-providers", value, id);
export const saveSignatureAccount = (value: SignatureAccountRequest, id?: string) => save<SignatureAccountRequest, SignatureAccount>("signature-accounts", value, id);
export const saveFseFacilityMapping = (value: FseFacilityMappingRequest, id?: string) => save<FseFacilityMappingRequest, FseFacilityMapping>("fse-facility-mappings", value, id);
export const saveSourceSystemFseDocumentTypes = (sourceSystemId: string, documentTypeCodes: string[]) =>
  request<SourceSystemFseDocumentType[]>(`technical-config/source-systems/${sourceSystemId}/fse-document-types`, {
    method: "PUT",
    body: JSON.stringify(documentTypeCodes.map((documentTypeCode) => ({ documentTypeCode, cdaInjectionEnabled: true }))),
  });
export const saveFseDocumentTypeSignaturePolicy = (code: string, approvalRequired: boolean, previewRequired: boolean) =>
  request<FseDocumentType>(`technical-config/fse-document-types/${code}/signature-policy`, {
    method: "PUT", body: JSON.stringify({ approvalRequired, previewRequired }),
  });
export const deleteTechnicalConfiguration = (resource: string, id: string) => request<void>(`technical-config/${resource}/${id}`, { method: "DELETE" });

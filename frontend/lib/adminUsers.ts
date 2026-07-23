export type Option = { id: string; code: string; name: string; active: boolean };

export type ApplicationUser = {
  id: string;
  username: string;
  oidcSubject: string;
  firstName: string;
  lastName: string;
  email?: string;
  fiscalCode?: string;
  signerFiscalCode?: string;
  counterSignerFiscalCode?: string;
  active: boolean;
  partition: Option;
  company: Option;
  roles: Option[];
  groups: Option[];
};

export type PageResponse<T> = { items: T[]; page: number; size: number; total: number };

export type OrganizationOptions = {
  partitions: Option[];
  companies: Option[];
  roles: Option[];
  groups: Option[];
};

export type OrganizationType = "partitions" | "companies" | "groups";
export type OrganizationItem = Option & { partitionId?: string | null };
export type OrganizationItemRequest = { code: string; name: string; partitionId?: string | null; active: boolean };
export type UiTexts = Record<string, string>;

export type ApplicationUserRequest = {
  username: string;
  oidcSubject: string;
  firstName: string;
  lastName: string;
  email: string;
  fiscalCode: string;
  signerFiscalCode: string;
  counterSignerFiscalCode: string;
  active: boolean;
  partitionId: string;
  companyId: string;
  roleIds: string[];
  groupIds: string[];
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/backend/admin/${path}`, {
    ...init,
    headers: { "content-type": "application/json", ...(init?.headers ?? {}) },
    cache: "no-store",
  });
  if (!response.ok) {
    throw new Error(`Admin request failed with status ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export async function fetchUsers(query = "") {
  const params = new URLSearchParams({ page: "0", size: "50" });
  if (query.trim()) params.set("query", query.trim());
  return request<PageResponse<ApplicationUser>>(`users?${params}`);
}

export async function fetchOrganizationOptions(): Promise<OrganizationOptions> {
  const [partitions, companies, roles, groups] = await Promise.all([
    request<Option[]>("organization/partitions"),
    request<Option[]>("organization/companies"),
    request<Option[]>("organization/roles"),
    request<Option[]>("organization/groups"),
  ]);
  return { partitions, companies, roles, groups };
}

export async function saveUser(user: ApplicationUserRequest, id?: string) {
  return request<ApplicationUser>(id ? `users/${id}` : "users", {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(user),
  });
}

export async function setUserActive(id: string, active: boolean) {
  return request<ApplicationUser>(`users/${id}/${active ? "activate" : "deactivate"}`, { method: "POST" });
}

export function fetchOrganizationItems(type: OrganizationType) {
  return request<OrganizationItem[]>(`organization/manage/${type}`);
}

export function saveOrganizationItem(type: OrganizationType, item: OrganizationItemRequest, id?: string) {
  return request<OrganizationItem>(`organization/manage/${type}${id ? `/${id}` : ""}`, {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(item),
  });
}

export function setOrganizationItemActive(type: OrganizationType, id: string, active: boolean) {
  return request<OrganizationItem>(`organization/manage/${type}/${id}/${active ? "activate" : "deactivate"}`, { method: "POST" });
}

export function fetchUiTexts() {
  return request<UiTexts>("ui-texts");
}

export function saveUiTexts(texts: UiTexts) {
  return request<UiTexts>("ui-texts", { method: "PUT", body: JSON.stringify(texts) });
}

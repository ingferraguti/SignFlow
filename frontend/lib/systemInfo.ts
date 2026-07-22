export type SystemInfo = {
  applicationName: string;
  version: string;
  status: string;
};

export async function fetchSystemInfo(): Promise<SystemInfo> {
  const response = await fetch("/api/backend/system-info", { cache: "no-store" });

  if (!response.ok) {
    throw new Error(`System info request failed with status ${response.status}`);
  }

  return response.json() as Promise<SystemInfo>;
}

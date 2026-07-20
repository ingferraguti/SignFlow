export type SystemInfo = {
  applicationName: string;
  version: string;
  status: string;
};

export async function fetchSystemInfo(): Promise<SystemInfo> {
  const baseUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
  const response = await fetch(`${baseUrl}/api/system/info`, { cache: "no-store" });

  if (!response.ok) {
    throw new Error(`System info request failed with status ${response.status}`);
  }

  return response.json() as Promise<SystemInfo>;
}

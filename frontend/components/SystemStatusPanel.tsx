"use client";

import { useEffect, useState } from "react";
import { fetchSystemInfo, type SystemInfo } from "../lib/systemInfo";

export function SystemStatusPanel() {
  const [info, setInfo] = useState<SystemInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchSystemInfo().then(setInfo).catch((reason: Error) => setError(reason.message)).finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Loading system status…</p>;
  if (error) return <div className="card error" role="alert"><h2>System status unavailable</h2><p>{error}</p></div>;
  return <div className="card"><h1>System status</h1><p><strong>Application:</strong> {info?.applicationName}</p><p><strong>Version:</strong> {info?.version}</p><p><strong>Status:</strong> <span className="status">{info?.status}</span></p></div>;
}

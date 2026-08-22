"use client";

import { useEffect } from "react";
import { SessionProvider, useSession } from "next-auth/react";
import type { Session } from "next-auth";

export function AuthProvider({ children, session }: Readonly<{ children: React.ReactNode; session: Session | null }>) {
  return <SessionProvider session={session}><SessionAuditTracker />{children}</SessionProvider>;
}

function SessionAuditTracker() {
  const { data: session, status } = useSession();
  useEffect(() => {
    if (status !== "authenticated" || !session?.user) return;
    const username = session.user.username ?? session.user.email ?? session.user.name ?? "authenticated";
    const key = `signflow-login-audited:${username}`;
    if (sessionStorage.getItem(key)) return;
    fetch("/api/backend/session-audit/login", { method: "POST", headers: { "X-Correlation-ID": crypto.randomUUID() } })
      .then((response) => {
        if (response.ok) sessionStorage.setItem(key, "true");
        else console.warn("Session audit login could not be recorded", response.status);
      })
      .catch(() => console.warn("Session audit login request failed"));
  }, [session, status]);
  return null;
}

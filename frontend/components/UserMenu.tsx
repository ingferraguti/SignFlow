"use client";

import { signOut, useSession } from "next-auth/react";
import { useUiTexts } from "./UiTextProvider";

export function UserMenu() {
  const { data: session, status } = useSession();
  const { text } = useUiTexts();
  if (status === "loading") return <span className="user-menu">Checking session...</span>;
  if (!session?.user) return null;

  const username = session.user.username ?? session.user.email ?? session.user.name ?? "authenticated user";
  const roles = session.roles.length > 0 ? session.roles.join(", ") : "no mapped roles";

  async function logout() {
    const auditResponse = await fetch("/api/backend/session-audit/logout", { method: "POST", headers: { "X-Correlation-ID": crypto.randomUUID() }, keepalive: true }).catch(() => undefined);
    if (auditResponse && !auditResponse.ok) console.warn("Session audit logout could not be recorded", auditResponse.status);
    Object.keys(sessionStorage).filter((key) => key.startsWith("signflow-login-audited:")).forEach((key) => sessionStorage.removeItem(key));
    await signOut({ redirect: false });
    window.location.replace("/api/auth/keycloak-logout");
  }

  return (
    <div className="user-menu" aria-label="Current user">
      <span>Signed in as <strong>{username}</strong></span>
      <small>{roles}</small>
      <button onClick={logout}>{text("button.logout")}</button>
    </div>
  );
}

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
    await signOut({ redirect: false });
    const issuer = process.env.NEXT_PUBLIC_KEYCLOAK_EXTERNAL_ISSUER ?? "http://localhost:8081/realms/signflow";
    const returnTo = encodeURIComponent(`${window.location.origin}/login`);
    window.location.assign(`${issuer}/protocol/openid-connect/logout?client_id=signflow-frontend&post_logout_redirect_uri=${returnTo}`);
  }

  return (
    <div className="user-menu" aria-label="Current user">
      <span>Signed in as <strong>{username}</strong></span>
      <small>{roles}</small>
      <button onClick={logout}>{text("button.logout")}</button>
    </div>
  );
}

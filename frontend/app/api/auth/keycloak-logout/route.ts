import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

export async function GET() {
  const issuer = process.env.KEYCLOAK_EXTERNAL_ISSUER ?? "http://localhost:8081/realms/signflow";
  const applicationUrl = process.env.NEXTAUTH_URL ?? "http://localhost:3000";
  const logoutUrl = new URL(`${issuer}/protocol/openid-connect/logout`);
  logoutUrl.searchParams.set("client_id", process.env.KEYCLOAK_CLIENT_ID ?? "signflow-frontend");
  logoutUrl.searchParams.set("post_logout_redirect_uri", new URL("/login", applicationUrl).toString());
  return NextResponse.redirect(logoutUrl, { headers: { "Cache-Control": "no-store" } });
}

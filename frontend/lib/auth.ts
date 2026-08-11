import type { NextAuthOptions } from "next-auth";

const externalIssuer = process.env.KEYCLOAK_EXTERNAL_ISSUER ?? "http://localhost:8081/realms/signflow";
const internalIssuer = process.env.KEYCLOAK_INTERNAL_ISSUER ?? externalIssuer;

type KeycloakProfile = {
  sub: string;
  preferred_username?: string;
  name?: string;
  email?: string;
  realm_access?: { roles?: string[] };
};

function accessTokenRoles(accessToken: string): string[] {
  try {
    const payload = JSON.parse(Buffer.from(accessToken.split(".")[1], "base64url").toString("utf8")) as KeycloakProfile;
    return payload.realm_access?.roles?.filter((role) => role === "ADMINISTRATOR" || role === "SIGNER" || role === "APPROVER") ?? [];
  } catch {
    return [];
  }
}

export const authOptions: NextAuthOptions = {
  providers: [
    {
      id: "keycloak",
      name: "Keycloak",
      type: "oauth",
      clientId: process.env.KEYCLOAK_CLIENT_ID ?? "signflow-frontend",
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET ?? "local-demo-frontend-secret",
      authorization: {
        url: `${externalIssuer}/protocol/openid-connect/auth`,
        params: { scope: "openid profile email roles" },
      },
      token: `${internalIssuer}/protocol/openid-connect/token`,
      userinfo: `${internalIssuer}/protocol/openid-connect/userinfo`,
      jwks_endpoint: `${internalIssuer}/protocol/openid-connect/certs`,
      issuer: externalIssuer,
      checks: ["pkce", "state"],
      profile(profile: KeycloakProfile) {
        return {
          id: profile.sub,
          name: profile.name ?? profile.preferred_username ?? profile.sub,
          email: profile.email,
          username: profile.preferred_username ?? profile.sub,
          roles: profile.realm_access?.roles ?? [],
        };
      },
    },
  ],
  session: { strategy: "jwt" },
  callbacks: {
    jwt({ token, account, profile }) {
      if (account?.access_token) {
        token.accessToken = account.access_token;
        token.roles = accessTokenRoles(account.access_token);
      }
      if (account?.id_token) token.idToken = account.id_token;
      const keycloakProfile = profile as KeycloakProfile | undefined;
      if (keycloakProfile?.preferred_username) token.username = keycloakProfile.preferred_username;
      if (keycloakProfile?.realm_access?.roles) {
        token.roles = keycloakProfile.realm_access.roles.filter((role) => role === "ADMINISTRATOR" || role === "SIGNER" || role === "APPROVER");
      }
      return token;
    },
    session({ session, token }) {
      session.accessToken = typeof token.accessToken === "string" ? token.accessToken : undefined;
      session.idToken = typeof token.idToken === "string" ? token.idToken : undefined;
      session.roles = Array.isArray(token.roles) ? token.roles.filter((role): role is string => typeof role === "string") : [];
      if (session.user) {
        session.user.username = typeof token.username === "string" ? token.username : session.user.email ?? session.user.name ?? undefined;
      }
      return session;
    },
  },
};

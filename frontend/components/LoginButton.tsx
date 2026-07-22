"use client";

import { signIn } from "next-auth/react";

export function LoginButton() {
  return <button className="primary" onClick={() => signIn("keycloak", { callbackUrl: "/" })}>Login with Keycloak</button>;
}

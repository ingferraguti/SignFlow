"use client";

import { signIn } from "next-auth/react";
import { useUiTexts } from "./UiTextProvider";

export function LoginButton() {
  const { text } = useUiTexts();
  return <button className="primary" onClick={() => signIn("keycloak", { callbackUrl: "/" })}>{text("button.login")}</button>;
}

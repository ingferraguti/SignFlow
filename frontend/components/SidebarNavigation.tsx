"use client";

import Link from "next/link";
import { useSession } from "next-auth/react";
import { useUiTexts } from "./UiTextProvider";

const adminNavigation = [
  { href: "/", key: "menu.home", enabled: true },
  { href: "/system", key: "menu.system", enabled: true },
  { href: "/referti", key: "menu.reports", enabled: true },
  { href: "/monitoraggio", key: "menu.monitoring", enabled: false },
  { href: "/configurazione", key: "menu.configuration", enabled: true },
];

const signerNavigation = [
  { href: "/firma", key: "menu.signerHome" },
  { href: "/firma/referti", key: "menu.signerReports" },
  { href: "/firma/stati", key: "menu.signerStates" },
  { href: "/firma/informazioni", key: "menu.signerInfo" },
  { href: "/firma/profilo", key: "menu.signerProfile" },
];

export function SidebarNavigation() {
  const { text } = useUiTexts();
  const { data: session } = useSession();
  const isAdmin = session?.roles?.includes("ADMINISTRATOR");
  const isSigner = session?.roles?.includes("SIGNER");
  return <aside className="sidebar" aria-label="Navigazione principale"><nav>
    {isAdmin ? adminNavigation.map((item) => item.enabled
      ? <Link key={item.href} href={item.href}>{text(item.key)}</Link>
      : <span key={item.href} aria-disabled="true">{text(item.key)}<small>planned</small></span>) : null}
    {isSigner ? signerNavigation.map((item) => <Link key={item.href} href={item.href}>{text(item.key)}</Link>) : null}
  </nav></aside>;
}

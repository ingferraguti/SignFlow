"use client";

import Link from "next/link";
import { useUiTexts } from "./UiTextProvider";

const navigation = [
  { href: "/", key: "menu.home", enabled: true },
  { href: "/system", key: "menu.system", enabled: true },
  { href: "/referti", key: "menu.reports", enabled: true },
  { href: "/firma", key: "menu.signature", enabled: false },
  { href: "/monitoraggio", key: "menu.monitoring", enabled: false },
  { href: "/configurazione", key: "menu.configuration", enabled: true },
];

export function SidebarNavigation() {
  const { text } = useUiTexts();
  return <aside className="sidebar" aria-label="Main navigation"><nav>{navigation.map((item) => item.enabled
    ? <Link key={item.href} href={item.href}>{text(item.key)}</Link>
    : <span key={item.href} aria-disabled="true">{text(item.key)}<small>planned</small></span>)}</nav></aside>;
}

import type { Metadata } from "next";
import Link from "next/link";
import { AuthProvider } from "../components/AuthProvider";
import { UserMenu } from "../components/UserMenu";
import "./globals.css";

export const metadata: Metadata = { title: "SignFlow", description: "Clinical document signature workflow foundation" };

const navigation = [
  { href: "/", label: "Home", enabled: true },
  { href: "/system", label: "System status", enabled: true },
  { href: "/referti", label: "Referti", enabled: false },
  { href: "/firma", label: "Firma", enabled: false },
  { href: "/monitoraggio", label: "Monitoraggio", enabled: false },
  { href: "/configurazione", label: "Configurazione", enabled: false },
];

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body><AuthProvider><div className="shell"><header className="topbar"><div><strong>SignFlow</strong><span>Remote clinical document signature middleware</span></div><UserMenu /></header><div className="content"><aside className="sidebar" aria-label="Main navigation"><nav>{navigation.map((item) => item.enabled ? <Link key={item.href} href={item.href}>{item.label}</Link> : <span key={item.href} aria-disabled="true">{item.label}<small> planned</small></span>)}</nav></aside><main className="main">{children}</main></div></div></AuthProvider></body></html>;
}

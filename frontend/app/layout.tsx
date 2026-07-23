import type { Metadata } from "next";
import { AuthProvider } from "../components/AuthProvider";
import { UserMenu } from "../components/UserMenu";
import { SidebarNavigation } from "../components/SidebarNavigation";
import { UiTextProvider } from "../components/UiTextProvider";
import "./globals.css";

export const metadata: Metadata = { title: "SignFlow", description: "Clinical document signature workflow foundation" };

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="it"><body><AuthProvider><UiTextProvider><div className="shell"><header className="topbar"><div><strong>SignFlow</strong><span>Remote clinical document signature middleware</span></div><UserMenu /></header><div className="content"><SidebarNavigation /><main className="main">{children}</main></div></div></UiTextProvider></AuthProvider></body></html>;
}

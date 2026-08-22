import type { Metadata } from "next";
import { getServerSession } from "next-auth";
import { AuthProvider } from "../components/AuthProvider";
import { UserMenu } from "../components/UserMenu";
import { SidebarNavigation } from "../components/SidebarNavigation";
import { UiTextProvider } from "../components/UiTextProvider";
import { authOptions } from "../lib/auth";
import "./globals.css";

export const metadata: Metadata = { title: "SignFlow", description: "Clinical document signature workflow foundation" };

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const session = await getServerSession(authOptions);
  return <html lang="it"><body><AuthProvider session={session}><UiTextProvider><div className="shell"><header className="topbar"><div><strong>SignFlow</strong><span>Remote clinical document signature middleware</span></div><UserMenu /></header><div className="content"><SidebarNavigation /><main className="main">{children}</main></div></div></UiTextProvider></AuthProvider></body></html>;
}

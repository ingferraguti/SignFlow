import { getServerSession } from "next-auth";
import { NextResponse } from "next/server";
import { authOptions } from "../../../../lib/auth";

export async function GET() {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  const backendUrl = process.env.BACKEND_INTERNAL_URL ?? process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
  const response = await fetch(`${backendUrl}/api/ui-texts`, {
    headers: { Authorization: `Bearer ${session.accessToken}` }, cache: "no-store",
  });
  return new NextResponse(await response.arrayBuffer(), {
    status: response.status, headers: { "content-type": response.headers.get("content-type") ?? "application/json" },
  });
}

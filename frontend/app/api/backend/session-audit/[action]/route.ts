import { getServerSession } from "next-auth";
import { NextRequest, NextResponse } from "next/server";
import { authOptions } from "../../../../../lib/auth";

type Context = { params: Promise<{ action: string }> };

export async function POST(request: NextRequest, context: Context) {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  const { action } = await context.params;
  if (action !== "login" && action !== "logout") return NextResponse.json({ message: "Unknown session event" }, { status: 404 });
  const backendUrl = process.env.BACKEND_INTERNAL_URL ?? process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
  const response = await fetch(`${backendUrl}/api/session-audit/${action}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${session.accessToken}`, "X-Correlation-ID": request.headers.get("X-Correlation-ID") ?? crypto.randomUUID() },
    cache: "no-store",
  });
  if (!response.ok) console.warn(`Session audit backend returned ${response.status}`);
  return new NextResponse(null, { status: response.status });
}

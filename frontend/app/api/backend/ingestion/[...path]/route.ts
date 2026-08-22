import { getServerSession } from "next-auth";
import { NextRequest, NextResponse } from "next/server";
import { authOptions } from "../../../../../lib/auth";

type Context = { params: Promise<{ path: string[] }> };

async function proxy(request: NextRequest, context: Context) {
  const session = await getServerSession(authOptions);
  if (!session?.accessToken) {
    return NextResponse.json({ message: "Authentication required" }, { status: 401 });
  }

  const { path } = await context.params;
  const backendUrl = process.env.BACKEND_INTERNAL_URL ?? process.env.NEXT_PUBLIC_BACKEND_URL ?? "http://localhost:8080";
  const target = new URL(`${backendUrl}/api/ingestion/${path.join("/")}`);
  target.search = request.nextUrl.search;
  const headers = new Headers({
    Authorization: `Bearer ${session.accessToken}`,
    "content-type": request.headers.get("content-type") ?? "application/hl7-v2",
    "X-Correlation-ID": request.headers.get("X-Correlation-ID") ?? crypto.randomUUID(),
  });
  for (const name of ["X-Source-System", "X-Idempotency-Key"]) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }

  const response = await fetch(target, {
    method: request.method,
    headers,
    body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
    cache: "no-store",
  });
  const responseHeaders = new Headers({
    "content-type": response.headers.get("content-type") ?? "application/json",
  });
  for (const name of ["cache-control", "x-content-type-options", "x-correlation-id"]) {
    const value = response.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  return new NextResponse(await response.arrayBuffer(), { status: response.status, headers: responseHeaders });
}

export { proxy as POST };

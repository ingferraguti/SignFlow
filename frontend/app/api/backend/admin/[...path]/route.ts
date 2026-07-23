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
  const target = new URL(`${backendUrl}/api/admin/${path.join("/")}`);
  target.search = request.nextUrl.search;

  const response = await fetch(target, {
    method: request.method,
    headers: {
      Authorization: `Bearer ${session.accessToken}`,
      "content-type": request.headers.get("content-type") ?? "application/json",
    },
    body: request.method === "GET" || request.method === "HEAD" ? undefined : await request.arrayBuffer(),
    cache: "no-store",
  });

  const body = await response.arrayBuffer();
  const headers = new Headers();
  headers.set("content-type", response.headers.get("content-type") ?? "application/json");
  for (const name of ["content-disposition", "content-length", "x-content-type-options"]) {
    const value = response.headers.get(name);
    if (value) headers.set(name, value);
  }
  return new NextResponse(body, {
    status: response.status,
    headers,
  });
}

export { proxy as GET, proxy as POST, proxy as PUT, proxy as DELETE };

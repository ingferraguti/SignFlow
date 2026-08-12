import { withAuth } from "next-auth/middleware";

const authenticationProxy = withAuth({
  pages: { signIn: "/login" },
});

export default authenticationProxy;

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|login).*)"],
};

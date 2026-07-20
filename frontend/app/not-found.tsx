import Link from "next/link";

export default function NotFound() {
  return <section className="card"><h1>Page not found</h1><p>The requested SignFlow page does not exist.</p><Link href="/">Return to home</Link></section>;
}

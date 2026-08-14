import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "AI Future Process Designer — AssessWise",
  description:
    "Analyses a current-state business process and designs its AI-enabled future state as structured, queryable data.",
};

function NavLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className="rounded-md px-3 py-1.5 text-sm font-medium text-ink-600 transition-colors hover:bg-ink-100 hover:text-ink-900"
    >
      {children}
    </Link>
  );
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN">
      <body className="min-h-screen antialiased">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2 focus:shadow"
        >
          Skip to content
        </a>

        <header className="sticky top-0 z-40 border-b border-ink-200 bg-white/90 backdrop-blur">
          <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6 lg:px-8">
            <Link href="/" className="flex items-center gap-2.5">
              <span
                aria-hidden="true"
                className="grid size-8 place-items-center rounded-lg bg-brand-600 text-sm font-bold text-white"
              >
                AI
              </span>
              <span>
                <span className="block text-sm leading-tight font-semibold text-ink-900">
                  Future Process Designer
                </span>
                <span className="block text-xs leading-tight text-ink-500">
                  AssessWise · Online Education &amp; Digital Assessment
                </span>
              </span>
            </Link>
            <nav className="ml-auto flex items-center gap-1">
              <NavLink href="/">Processes</NavLink>
              <NavLink href="/how-it-works">How it works</NavLink>
              <NavLink href="/evidence">Evidence</NavLink>
              <NavLink href="/processes/new">+ New process</NavLink>
            </nav>
          </div>
        </header>

        <main id="main" className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          {children}
        </main>

        <footer className="mx-auto max-w-7xl px-4 pt-4 pb-10 text-xs text-ink-500 sm:px-6 lg:px-8">
          Every future state on this site is generated at request time by the analysis pipeline and
          stored as rows in PostgreSQL. Nothing is pre-written.
        </footer>
      </body>
    </html>
  );
}

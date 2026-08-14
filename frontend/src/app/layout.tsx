import type { Metadata } from "next";
import { RequireAuth } from "@/components/require-auth";
import { SiteHeader } from "@/components/site-header";
import { AuthProvider } from "@/lib/auth-context";
import "./globals.css";

export const metadata: Metadata = {
  title: "AI Future Process Designer — AssessWise",
  description:
    "Describe a business process as it runs today and get back an AI-enabled redesign, stored as structured, queryable data rather than prose.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN">
      <body className="min-h-screen antialiased">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2 focus:shadow-lg"
        >
          Skip to content
        </a>

        <AuthProvider>
          <SiteHeader />

          <main id="main" className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
            <RequireAuth>{children}</RequireAuth>
          </main>
        </AuthProvider>

        <footer className="mx-auto max-w-7xl px-4 pt-6 pb-12 sm:px-6 lg:px-8">
          <p className="border-t border-ink-200 pt-6 text-xs leading-relaxed text-ink-500">
            Every redesign on this site is generated at the moment you ask for it and stored as rows
            in PostgreSQL — never written in advance. Open{" "}
            <span className="font-medium text-ink-700">Show prompt &amp; raw response</span> on any
            analysed process to see exactly what was sent to the AI and exactly what came back.
          </p>
        </footer>
      </body>
    </html>
  );
}

import type { Metadata } from "next";
import { AppShell } from "@/components/app-shell";
import { RequireAuth } from "@/components/require-auth";
import { EvidenceProvider } from "@/components/evidence";
import { ToastProvider } from "@/components/ui";
import { AuthProvider } from "@/lib/auth-context";
import "./globals.css";

export const metadata: Metadata = {
  title: "AI Future Process Designer — AssessWise",
  description:
    "Describe a business process as it runs today and get back an AI-enabled redesign: researched live against public sources, every recommendation citing a quote that was checked against the page it came from, and stored as structured, queryable data rather than prose.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en-IN" suppressHydrationWarning>
      <head>
        {/*
          Applies the stored theme before first paint.
          Without this the page renders light, then flips — which on a dark-mode machine is a
          white flash at every navigation. It has to be inline and synchronous to beat paint,
          and it is deliberately tiny: read one key, set one attribute, fail silently if storage
          is unavailable (a private window, or a browser blocking site data).
        */}
        <script
          dangerouslySetInnerHTML={{
            __html: `try{var t=localStorage.getItem('afpd.theme');if(t==='dark'||t==='light'){document.documentElement.dataset.theme=t}}catch(e){}`,
          }}
        />
      </head>
      <body className="min-h-screen antialiased">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-lg focus:bg-[var(--surface-2)] focus:px-3 focus:py-2 focus:shadow-[var(--shadow-pop)]"
        >
          Skip to content
        </a>

        <AuthProvider>
          <ToastProvider>
            <EvidenceProvider>
              <AppShell>
                <RequireAuth>{children}</RequireAuth>
              </AppShell>
            </EvidenceProvider>
          </ToastProvider>
        </AuthProvider>
      </body>
    </html>
  );
}

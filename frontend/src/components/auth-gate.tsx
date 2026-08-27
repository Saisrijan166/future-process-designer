"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell, ThemeToggle } from "@/components/app-shell";
import { Spinner } from "@/components/ui";
import { useAuth } from "@/lib/auth-context";

/** Pages reachable without a session. Everything else redirects to sign-in. */
const PUBLIC_PATHS = ["/login"];

/**
 * Decides what chrome a visitor gets, and whether they get the app at all.
 *
 * <p>The gate sits *outside* the application shell rather than inside it, which is the whole point:
 * the sidebar advertises Processes, Evidence and Engine, none of which a signed-out visitor can
 * open. Rendering it around the sign-in form promised things the visitor could not have.
 *
 * <p>This is a convenience, not a security boundary. The guarantee that one account cannot read
 * another's work is enforced by the API, which checks ownership on every request and returns 404
 * for a process that is not yours. Removing this component would make the UI unpleasant, not
 * insecure.
 */
export function AuthGate({ children }: { children: React.ReactNode }) {
  const { user, initialising } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const isPublic = PUBLIC_PATHS.includes(pathname);

  useEffect(() => {
    if (initialising || user || isPublic) return;
    // Where they were trying to go, so signing in puts them there rather than at the dashboard.
    // The path only — a query string is not worth an open-redirect surface.
    const next = pathname === "/" ? "" : `?next=${encodeURIComponent(pathname)}`;
    router.replace(`/login${next}`);
  }, [initialising, user, isPublic, pathname, router]);

  // Checking the stored token. A spinner rather than the sign-in screen, so someone who *is*
  // signed in does not see a flash of "signed out" on every reload.
  if (initialising) {
    return <Interstitial>Checking your session…</Interstitial>;
  }

  if (isPublic) {
    return <PublicFrame>{children}</PublicFrame>;
  }

  if (!user) {
    // The redirect above is in flight. Saying so beats a blank page.
    return <Interstitial>Taking you to sign-in…</Interstitial>;
  }

  return <AppShell>{children}</AppShell>;
}

/**
 * Chrome for a signed-out page: the wordmark and the theme toggle, nothing that leads anywhere
 * requiring an account.
 */
function PublicFrame({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-[var(--surface-page)]">
      <header className="no-print flex items-center gap-2.5 px-4 py-3 sm:px-6">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-[var(--surface-inverse)]">
          <svg viewBox="0 0 20 20" className="size-4 text-[var(--text-inverse)]" aria-hidden="true">
            <path
              d="M4 14.5L8 6l3 5.5L13 8l3 6.5"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        <span className="text-sm font-semibold">Future Process Designer</span>
        <div className="ml-auto">
          <ThemeToggle />
        </div>
      </header>
      <main id="main" className="flex flex-1 flex-col px-4 pb-10 sm:px-6">
        {children}
      </main>
    </div>
  );
}

function Interstitial({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="flex min-h-screen items-center justify-center gap-3 bg-[var(--surface-page)] text-sm text-[var(--text-secondary)]"
      role="status"
    >
      <Spinner className="size-5 text-[var(--text-muted)]" />
      {children}
    </div>
  );
}

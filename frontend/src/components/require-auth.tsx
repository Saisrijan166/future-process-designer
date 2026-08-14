"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { Spinner } from "@/components/ui";
import { useAuth } from "@/lib/auth-context";

/** Pages reachable without a session. Everything else redirects to sign-in. */
const PUBLIC_PATHS = ["/login"];

/**
 * Gate around the application shell.
 *
 * <p>This is a convenience, not a security boundary: the guarantee that one account cannot read
 * another's work is enforced by the API, which checks ownership on every request and returns 404
 * for a process that is not yours. Removing this component would make the UI unpleasant, not
 * insecure.
 */
export function RequireAuth({ children }: { children: React.ReactNode }) {
  const { user, initialising } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const isPublic = PUBLIC_PATHS.includes(pathname);

  useEffect(() => {
    if (!initialising && !user && !isPublic) {
      router.replace("/login");
    }
  }, [initialising, user, isPublic, router]);

  if (isPublic) {
    return <>{children}</>;
  }

  // Checking the stored token. Showing a spinner rather than the sign-in screen avoids a flash of
  // "signed out" on every reload for someone who is, in fact, signed in.
  if (initialising) {
    return (
      <div className="flex min-h-64 items-center justify-center gap-3 text-sm text-ink-500">
        <Spinner className="size-5 text-ink-400" />
        Checking your session…
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return <>{children}</>;
}

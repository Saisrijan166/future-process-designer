"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Button, ErrorPanel, FormField, INPUT_CLASSES, Panel, Spinner } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

/**
 * Where to go once there is a session.
 *
 * <p>Only a same-site absolute path is accepted. `//evil.example` is a protocol-relative URL that
 * a browser treats as another origin, so the second character has to be checked as well as the
 * first — this is the shape of every open-redirect bug.
 */
function safeNext(raw: string | null): string {
  if (!raw || !raw.startsWith("/") || raw.startsWith("//")) return "/";
  return raw;
}

/**
 * Sign in, or create an account.
 *
 * <p>The demo credentials are printed on the page on purpose. This is a submitted piece of
 * coursework that a reviewer needs to open in under a minute, and making them hunt for a password
 * in a README is a worse trade than showing an account whose only privilege is creating processes
 * on a free-tier database.
 */
export default function LoginPage() {
  // useSearchParams needs a boundary above it, or this route cannot be prerendered at all.
  return (
    <Suspense fallback={<LoginFallback />}>
      <LoginForm />
    </Suspense>
  );
}

function LoginFallback() {
  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <Spinner className="size-5 text-[var(--text-muted)]" />
    </div>
  );
}

function LoginForm() {
  const { signIn, signUp, user } = useAuth();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [mode, setMode] = useState<"signin" | "signup">("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const fieldErrors = error?.fieldErrors ?? {};

  // Covers both halves of the same problem: the moment a sign-in succeeds, and someone arriving
  // here who already has a session. Before this, a successful sign-in left you looking at the form
  // you had just filled in, with no indication anything had happened.
  useEffect(() => {
    if (user) router.replace(next);
  }, [user, next, router]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (mode === "signin") {
        await signIn(email.trim(), password);
      } else {
        await signUp(email.trim(), password, displayName.trim() || undefined);
      }
      // Navigation is left to the effect above, which fires once the session is really in place.
      // Keeping the button in its loading state until the new page paints stops a second submit.
      return;
    } catch (caught) {
      setError(caught as ApiError);
      setSubmitting(false);
    }
  }

  function useDemoAccount() {
    setEmail("demo@assesswise.test");
    setPassword("demo12345");
    setMode("signin");
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-md flex-col justify-center py-8">
      <div className="mb-6 text-center">
        <span className="mx-auto mb-3 flex size-11 items-center justify-center rounded-xl bg-[var(--surface-inverse)] text-[var(--text-inverse)]">
          <svg viewBox="0 0 20 20" className="size-5" fill="none" aria-hidden="true">
            <path
              d="M4 14.5L8 6l3 5.5L13 8l3 6.5"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
        <h1 className="text-xl font-semibold">
          {mode === "signin" ? "Sign in" : "Create an account"}
        </h1>
        <p className="mt-1.5 text-sm leading-relaxed text-[var(--text-secondary)]">
          {mode === "signin"
            ? "Processes you create are private to your account. The samples are shared with everyone."
            : "Takes a moment. Anything you create stays private to you."}
        </p>
      </div>

      {error ? (
        <div className="mb-4">
          <ErrorPanel
            title={mode === "signin" ? "Could not sign in" : "Could not create the account"}
            message={error.message}
            detail={
              Object.keys(fieldErrors).length > 0
                ? Object.entries(fieldErrors)
                    .map(([field, message]) => `${field}: ${message}`)
                    .join(" · ")
                : undefined
            }
          />
        </div>
      ) : null}

      <Panel className="p-5">
        <form onSubmit={handleSubmit} className="space-y-4">
          <FormField label="Email" htmlFor="email" required error={fieldErrors.email}>
            <input
              id="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className={INPUT_CLASSES}
              placeholder="you@company.com"
              required
            />
          </FormField>

          {mode === "signup" ? (
            <FormField
              label="Display name"
              htmlFor="displayName"
              hint="Optional — the part before the @ is used if you leave it blank."
            >
              <input
                id="displayName"
                autoComplete="name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                className={INPUT_CLASSES}
                placeholder="Alex Kumar"
              />
            </FormField>
          ) : null}

          <FormField
            label="Password"
            htmlFor="password"
            required
            error={fieldErrors.password}
            hint={mode === "signup" ? "At least 8 characters." : undefined}
          >
            <input
              id="password"
              type="password"
              autoComplete={mode === "signin" ? "current-password" : "new-password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={INPUT_CLASSES}
              minLength={mode === "signup" ? 8 : undefined}
              required
            />
          </FormField>

          <Button type="submit" variant="primary" size="lg" loading={submitting} className="w-full">
            {mode === "signin" ? "Sign in" : "Create account"}
          </Button>
        </form>

        <div className="mt-4 border-t border-[var(--border-subtle)] pt-4 text-center">
          <button
            type="button"
            onClick={() => {
              setMode(mode === "signin" ? "signup" : "signin");
              setError(null);
            }}
            className="text-xs text-[var(--text-link)] hover:underline"
          >
            {mode === "signin" ? "Need an account? Create one" : "Already have an account? Sign in"}
          </button>
        </div>
      </Panel>

      <Panel quiet className="mt-4 p-4">
        <p className="text-xs leading-relaxed text-[var(--text-secondary)]">
          <strong className="text-[var(--text-primary)]">Reviewing this?</strong> Use the demo account —
          it can do everything a real one can.
        </p>
        <div className="mono mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[var(--text-muted)]">
          <span>demo@assesswise.test</span>
          <span>demo12345</span>
        </div>
        <Button size="sm" className="mt-2.5" onClick={useDemoAccount}>
          Fill it in
        </Button>
      </Panel>
    </div>
  );
}

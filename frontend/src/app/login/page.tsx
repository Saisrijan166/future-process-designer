"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Button, Card, ErrorPanel, Field, INPUT_CLASSES, Spinner } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";

type Mode = "signin" | "signup";

export default function LoginPage() {
  const router = useRouter();
  const { user, initialising, signIn, signUp } = useAuth();

  const [mode, setMode] = useState<Mode>("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  // Already signed in — nothing to do here.
  useEffect(() => {
    if (!initialising && user) {
      router.replace("/");
    }
  }, [initialising, user, router]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === "signin") {
        await signIn(email, password);
      } else {
        await signUp(email, password, displayName || undefined);
      }
      router.replace("/");
    } catch (caught) {
      setError(caught as ApiError);
      setBusy(false);
    }
  }

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
  }

  const fieldErrors = error?.fieldErrors ?? {};

  return (
    <div className="mx-auto max-w-md py-6">
      <div className="mb-6 text-center">
        <span
          aria-hidden="true"
          className="mx-auto mb-4 grid size-12 place-items-center rounded-2xl bg-ink-900 text-white"
        >
          <svg className="size-6" viewBox="0 0 20 20" fill="none">
            <path d="M3 5.5h5M3 10h9M3 14.5h5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            <path d="M14.5 3.5l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9.9-2.4Z" fill="currentColor" />
          </svg>
        </span>
        <h1 className="text-2xl font-semibold tracking-tight text-ink-900">
          {mode === "signin" ? "Sign in" : "Create an account"}
        </h1>
        <p className="mt-2 text-sm leading-relaxed text-ink-600">
          {mode === "signin"
            ? "Your processes are private to your account. The sample processes are shared with everyone."
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

      <Card className="p-6">
        <form onSubmit={handleSubmit} className="space-y-4">
          <Field label="Email" htmlFor="email" required error={fieldErrors.email}>
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
          </Field>

          {mode === "signup" ? (
            <Field
              label="Display name"
              htmlFor="displayName"
              hint="Optional — we use the part before the @ if you leave it blank."
            >
              <input
                id="displayName"
                autoComplete="name"
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
                className={INPUT_CLASSES}
                placeholder="Alex Kumar"
              />
            </Field>
          ) : null}

          <Field
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
          </Field>

          <Button type="submit" disabled={busy} className="w-full">
            {busy ? <Spinner /> : null}
            {busy ? "Please wait…" : mode === "signin" ? "Sign in" : "Create account"}
          </Button>
        </form>

        <p className="mt-4 border-t border-ink-100 pt-4 text-center text-sm text-ink-600">
          {mode === "signin" ? (
            <>
              No account?{" "}
              <button
                type="button"
                onClick={() => switchMode("signup")}
                className="font-medium text-brand-700 hover:underline"
              >
                Create one
              </button>
            </>
          ) : (
            <>
              Already have one?{" "}
              <button
                type="button"
                onClick={() => switchMode("signin")}
                className="font-medium text-brand-700 hover:underline"
              >
                Sign in
              </button>
            </>
          )}
        </p>
      </Card>

      {mode === "signin" ? (
        <Card className="mt-4 bg-ink-50 p-4">
          <p className="text-xs font-semibold text-ink-700">Just here to look around?</p>
          <p className="mt-1 text-xs leading-relaxed text-ink-600">
            Sign in with <code className="rounded bg-white px-1 py-0.5">demo@assesswise.test</code> /{" "}
            <code className="rounded bg-white px-1 py-0.5">demo12345</code>, or use the button below.
          </p>
          <Button
            type="button"
            variant="secondary"
            className="mt-3 w-full"
            disabled={busy}
            onClick={() => {
              setEmail("demo@assesswise.test");
              setPassword("demo12345");
              setMode("signin");
            }}
          >
            Fill the demo account
          </Button>
        </Card>
      ) : null}
    </div>
  );
}

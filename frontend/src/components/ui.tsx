import Link from "next/link";
import type { ReactNode } from "react";
import type { Tone } from "@/lib/format";

const TONE_CLASSES: Record<Tone, string> = {
  neutral: "bg-ink-100 text-ink-700 ring-ink-200",
  info: "bg-sky-50 text-sky-800 ring-sky-200",
  success: "bg-emerald-50 text-emerald-800 ring-emerald-200",
  warning: "bg-amber-50 text-amber-900 ring-amber-200",
  danger: "bg-rose-50 text-rose-800 ring-rose-200",
  accent: "bg-brand-50 text-brand-700 ring-brand-200",
};

export function Badge({
  children,
  tone = "neutral",
  title,
}: {
  children: ReactNode;
  tone?: Tone;
  title?: string;
}) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset whitespace-nowrap ${TONE_CLASSES[tone]}`}
    >
      {children}
    </span>
  );
}

export function Card({
  children,
  className = "",
  as: Component = "div",
}: {
  children: ReactNode;
  className?: string;
  as?: "div" | "section" | "article" | "li";
}) {
  return (
    <Component className={`rounded-xl border border-ink-200 bg-white shadow-xs ${className}`}>
      {children}
    </Component>
  );
}

export function SectionHeading({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h2 className="text-lg font-semibold text-ink-900">{title}</h2>
        {description ? <p className="mt-1 max-w-3xl text-sm text-ink-600">{description}</p> : null}
      </div>
      {action}
    </div>
  );
}

export function Spinner({ className = "size-4" }: { className?: string }) {
  return (
    <svg className={`animate-spin ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle className="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path
        className="opacity-90"
        fill="currentColor"
        d="M4 12a8 8 0 0 1 8-8V0C5.373 0 0 5.373 0 12h4z"
      />
    </svg>
  );
}

export function Loading({ label = "Loading…" }: { label?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center justify-center gap-3 rounded-xl border border-ink-200 bg-white p-10 text-sm text-ink-600"
    >
      <Spinner className="size-5 text-brand-600" />
      {label}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-xl border border-dashed border-ink-300 bg-white p-10 text-center">
      <p className="text-sm font-medium text-ink-800">{title}</p>
      <p className="mx-auto mt-1 max-w-md text-sm text-ink-500">{description}</p>
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </div>
  );
}

export function ErrorPanel({
  title = "Something went wrong",
  message,
  detail,
  onRetry,
}: {
  title?: string;
  message: string;
  detail?: string;
  onRetry?: () => void;
}) {
  return (
    <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-5">
      <p className="text-sm font-semibold text-rose-900">{title}</p>
      <p className="mt-1 text-sm text-rose-800">{message}</p>
      {detail ? (
        <p className="mt-2 rounded-md bg-white/70 p-2 font-mono text-xs break-words text-rose-700">
          {detail}
        </p>
      ) : null}
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded-md border border-rose-300 bg-white px-3 py-1.5 text-sm font-medium text-rose-800 hover:bg-rose-100"
        >
          Try again
        </button>
      ) : null}
    </div>
  );
}

export function Stat({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) {
  return (
    <div className="rounded-lg border border-ink-200 bg-white px-3 py-2">
      <dt className="text-xs font-medium tracking-wide text-ink-500 uppercase">{label}</dt>
      <dd className="mt-0.5 text-xl font-semibold text-ink-900">{value}</dd>
      {hint ? <p className="text-xs text-ink-500">{hint}</p> : null}
    </div>
  );
}

const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60";

const BUTTON_VARIANTS = {
  primary: "bg-brand-600 text-white hover:bg-brand-700",
  secondary: "border border-ink-300 bg-white text-ink-700 hover:bg-ink-50",
  danger: "border border-rose-300 bg-white text-rose-700 hover:bg-rose-50",
} as const;

export function Button({
  children,
  variant = "primary",
  className = "",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: keyof typeof BUTTON_VARIANTS;
}) {
  return (
    <button {...props} className={`${BUTTON_BASE} ${BUTTON_VARIANTS[variant]} ${className}`}>
      {children}
    </button>
  );
}

export function ButtonLink({
  href,
  children,
  variant = "primary",
  className = "",
}: {
  href: string;
  children: ReactNode;
  variant?: keyof typeof BUTTON_VARIANTS;
  className?: string;
}) {
  return (
    <Link href={href} className={`${BUTTON_BASE} ${BUTTON_VARIANTS[variant]} ${className}`}>
      {children}
    </Link>
  );
}

/** A labelled field with consistent spacing, help text and error rendering. */
export function Field({
  label,
  htmlFor,
  hint,
  error,
  required,
  children,
}: {
  label: string;
  htmlFor: string;
  hint?: string;
  error?: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block text-sm font-medium text-ink-800">
        {label}
        {required ? <span className="ml-0.5 text-rose-600">*</span> : null}
      </label>
      {hint ? <p className="mt-0.5 text-xs text-ink-500">{hint}</p> : null}
      <div className="mt-1.5">{children}</div>
      {error ? <p className="mt-1 text-xs font-medium text-rose-700">{error}</p> : null}
    </div>
  );
}

export const INPUT_CLASSES =
  "block w-full rounded-lg border border-ink-300 bg-white px-3 py-2 text-sm text-ink-900 placeholder:text-ink-400 focus:border-brand-500 focus:outline-none";

/** Renders text that may be absent, without leaving a hole in the layout. */
export function Text({ value, fallback = "—" }: { value?: string | null; fallback?: string }) {
  const trimmed = value?.trim();
  return trimmed ? (
    <>{trimmed}</>
  ) : (
    <span className="text-ink-400">{fallback}</span>
  );
}

"use client";

import Link from "next/link";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useId,
  useRef,
  useState,
  type ReactNode,
} from "react";

/**
 * The primitives everything else is built from.
 *
 * <p>Hand-written rather than pulled from a component library, for a reason that shows up in the
 * result: this interface has an unusual job — presenting generated claims next to the evidence for
 * them — and its most-used components are ones no library ships. A citation chip that opens the
 * quote it refers to, a verification badge, a score meter that reads as a measurement rather than a
 * gauge. Those had to be built anyway, and building the surrounding buttons and cards to match is
 * cheaper than reconciling two visual systems.
 */

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";
type ButtonSize = "sm" | "md" | "lg";

const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-all " +
  "disabled:cursor-not-allowed disabled:opacity-50 active:translate-y-px select-none whitespace-nowrap";

const BUTTON_VARIANTS: Record<ButtonVariant, string> = {
  primary:
    "bg-[var(--surface-inverse)] text-[var(--text-inverse)] hover:opacity-90 shadow-[var(--shadow-card)]",
  secondary:
    "bg-[var(--surface-2)] text-[var(--text-primary)] border border-[var(--border-strong)] hover:bg-[var(--surface-3)]",
  ghost: "text-[var(--text-secondary)] hover:bg-[var(--surface-3)] hover:text-[var(--text-primary)]",
  danger:
    "bg-[var(--status-critical-wash)] text-[var(--status-critical-ink)] border border-[color-mix(in_oklab,var(--status-critical-ink)_30%,transparent)] hover:bg-[color-mix(in_oklab,var(--status-critical-ink)_15%,var(--status-critical-wash))]",
};

const BUTTON_SIZES: Record<ButtonSize, string> = {
  sm: "h-8 px-3 text-xs",
  md: "h-9.5 px-4 text-sm",
  lg: "h-11 px-5 text-sm",
};

export function Button({
  variant = "secondary",
  size = "md",
  className = "",
  loading = false,
  children,
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
}) {
  return (
    <button
      {...props}
      disabled={props.disabled || loading}
      className={`${BUTTON_BASE} ${BUTTON_VARIANTS[variant]} ${BUTTON_SIZES[size]} ${className}`}
    >
      {loading ? <Spinner className="size-3.5" /> : null}
      {children}
    </button>
  );
}

export function ButtonLink({
  href,
  variant = "secondary",
  size = "md",
  className = "",
  children,
  ...props
}: React.ComponentProps<typeof Link> & { variant?: ButtonVariant; size?: ButtonSize }) {
  return (
    <Link
      href={href}
      {...props}
      className={`${BUTTON_BASE} ${BUTTON_VARIANTS[variant]} ${BUTTON_SIZES[size]} ${className}`}
    >
      {children}
    </Link>
  );
}

export function Spinner({ className = "size-4" }: { className?: string }) {
  return (
    <svg className={`${className} animate-spin`} viewBox="0 0 16 16" fill="none" aria-hidden="true">
      <circle cx="8" cy="8" r="6.5" stroke="currentColor" strokeWidth="2" opacity="0.25" />
      <path d="M14.5 8A6.5 6.5 0 0 0 8 1.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

export function Panel({
  children,
  className = "",
  quiet = false,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & { quiet?: boolean }) {
  return (
    <div {...props} className={`${quiet ? "panel-quiet" : "panel"} ${className}`}>
      {children}
    </div>
  );
}

export function SectionHeading({
  title,
  hint,
  action,
  id,
}: {
  title: string;
  hint?: ReactNode;
  action?: ReactNode;
  id?: string;
}) {
  return (
    <div className="mb-3 flex flex-wrap items-end justify-between gap-3">
      <div className="min-w-0">
        <h2 id={id} className="text-[0.9375rem] font-semibold text-[var(--text-primary)]">
          {title}
        </h2>
        {hint ? <p className="mt-0.5 text-xs text-[var(--text-muted)]">{hint}</p> : null}
      </div>
      {action}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Badges
// ---------------------------------------------------------------------------

export type Tone = "neutral" | "brand" | "good" | "warning" | "serious" | "critical" | "info";

const TONE_STYLES: Record<Tone, string> = {
  neutral:
    "bg-[var(--surface-3)] text-[var(--text-secondary)] border-[var(--border-subtle)]",
  brand: "bg-[var(--brand-wash)] text-[var(--text-link)] border-[color-mix(in_oklab,var(--text-link)_25%,transparent)]",
  good: "bg-[var(--status-good-wash)] text-[var(--status-good-ink)] border-[color-mix(in_oklab,var(--status-good-ink)_25%,transparent)]",
  warning:
    "bg-[var(--status-warning-wash)] text-[var(--status-warning-ink)] border-[color-mix(in_oklab,var(--status-warning-ink)_25%,transparent)]",
  serious:
    "bg-[var(--status-serious-wash)] text-[var(--status-serious-ink)] border-[color-mix(in_oklab,var(--status-serious-ink)_25%,transparent)]",
  critical:
    "bg-[var(--status-critical-wash)] text-[var(--status-critical-ink)] border-[color-mix(in_oklab,var(--status-critical-ink)_25%,transparent)]",
  info: "bg-[color-mix(in_oklab,var(--seq-400)_12%,transparent)] text-[var(--text-primary)] border-[color-mix(in_oklab,var(--seq-400)_30%,transparent)]",
};

/**
 * A label with a tone.
 *
 * <p>Status tones always carry their own text, never colour alone — the same rule the charts
 * follow, applied here because a badge is the smallest place it is tempting to break it.
 */
export function Badge({
  tone = "neutral",
  children,
  icon,
  className = "",
  title,
}: {
  tone?: Tone;
  children: ReactNode;
  icon?: ReactNode;
  className?: string;
  title?: string;
}) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1 rounded-md border px-1.5 py-0.5 text-[0.6875rem] font-medium ${TONE_STYLES[tone]} ${className}`}
    >
      {icon}
      {children}
    </span>
  );
}

/** A dot plus a word. Used where a badge would be too heavy, e.g. in a dense table. */
export function StatusDot({ tone, label }: { tone: Tone; label: string }) {
  const colour: Record<Tone, string> = {
    neutral: "var(--text-muted)",
    brand: "var(--text-link)",
    good: "var(--status-good)",
    warning: "var(--status-warning)",
    serious: "var(--status-serious)",
    critical: "var(--status-critical)",
    info: "var(--seq-400)",
  };
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-[var(--text-secondary)]">
      <span
        className="size-1.5 shrink-0 rounded-full"
        style={{ backgroundColor: colour[tone] }}
        aria-hidden="true"
      />
      {label}
    </span>
  );
}

// ---------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------

export function ErrorPanel({
  title,
  message,
  onRetry,
  detail,
}: {
  title: string;
  message: string;
  onRetry?: () => void;
  detail?: string | null;
}) {
  return (
    <div
      role="alert"
      className="rounded-[var(--radius-card)] border border-[color-mix(in_oklab,var(--status-critical-ink)_30%,transparent)] bg-[var(--status-critical-wash)] p-4"
    >
      <div className="flex items-start gap-3">
        <svg viewBox="0 0 20 20" className="mt-0.5 size-4 shrink-0" aria-hidden="true">
          <circle cx="10" cy="10" r="8.25" fill="none" stroke="var(--status-critical)" strokeWidth="1.5" />
          <path d="M10 6v5" stroke="var(--status-critical)" strokeWidth="1.75" strokeLinecap="round" />
          <circle cx="10" cy="14" r="0.9" fill="var(--status-critical)" />
        </svg>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-[var(--status-critical-ink)]">{title}</p>
          <p className="mt-1 text-sm text-[var(--text-secondary)]">{message}</p>
          {detail ? (
            <p className="mono mt-2 rounded bg-[var(--surface-2)] p-2 text-[var(--text-muted)]">{detail}</p>
          ) : null}
          {onRetry ? (
            <Button size="sm" onClick={onRetry} className="mt-3">
              Try again
            </Button>
          ) : null}
        </div>
      </div>
    </div>
  );
}

export function EmptyState({
  title,
  message,
  action,
  icon,
}: {
  title: string;
  message: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-[var(--radius-card)] border border-dashed border-[var(--border-strong)] px-6 py-12 text-center">
      {icon ? <div className="mb-3 text-[var(--text-muted)]">{icon}</div> : null}
      <p className="text-sm font-semibold text-[var(--text-primary)]">{title}</p>
      <p className="mt-1 max-w-md text-sm text-[var(--text-secondary)]">{message}</p>
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  );
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 py-8 text-sm text-[var(--text-muted)]">
      <Spinner />
      {label}
    </div>
  );
}

export function Skeleton({ className = "h-4 w-full" }: { className?: string }) {
  return <div className={`skeleton ${className}`} aria-hidden="true" />;
}

// ---------------------------------------------------------------------------
// Toasts
// ---------------------------------------------------------------------------

interface Toast {
  id: number;
  tone: Tone;
  title: string;
  message?: string;
}

const ToastContext = createContext<{ push: (toast: Omit<Toast, "id">) => void } | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const push = useCallback((toast: Omit<Toast, "id">) => {
    const id = nextId.current++;
    setToasts((current) => [...current, { ...toast, id }]);
    // Long enough to read a two-line message, short enough not to stack up during a run.
    window.setTimeout(() => setToasts((current) => current.filter((entry) => entry.id !== id)), 6000);
  }, []);

  return (
    <ToastContext.Provider value={{ push }}>
      {children}
      <div
        className="no-print pointer-events-none fixed bottom-4 right-4 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
        role="status"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className="rise-in pointer-events-auto rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-2)] p-3 shadow-[var(--shadow-pop)]"
          >
            <div className="flex items-start gap-2">
              <Badge tone={toast.tone}>{toast.tone === "critical" ? "Failed" : "Done"}</Badge>
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-[var(--text-primary)]">{toast.title}</p>
                {toast.message ? (
                  <p className="mt-0.5 text-xs text-[var(--text-secondary)]">{toast.message}</p>
                ) : null}
              </div>
              <button
                type="button"
                onClick={() => setToasts((current) => current.filter((entry) => entry.id !== toast.id))}
                className="text-[var(--text-muted)] hover:text-[var(--text-primary)]"
                aria-label="Dismiss"
              >
                <svg viewBox="0 0 12 12" className="size-3" aria-hidden="true">
                  <path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                </svg>
              </button>
            </div>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    // A no-op rather than a throw: a component rendered outside the provider in a test should not
    // fail because of a notification it does not care about.
    return { push: () => {} };
  }
  return context;
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

export interface TabDefinition {
  id: string;
  label: string;
  count?: number | null;
  disabled?: boolean;
}

/**
 * A scrollable tab strip.
 *
 * <p>Counts sit in the tab because they answer "is there anything in there?" before the click. A
 * tab reading "Risks 9" and one reading "Risks" are different invitations.
 */
export function Tabs({
  tabs,
  active,
  onChange,
  className = "",
}: {
  tabs: TabDefinition[];
  active: string;
  onChange: (id: string) => void;
  className?: string;
}) {
  return (
    <div
      role="tablist"
      className={`no-print flex gap-1 overflow-x-auto border-b border-[var(--border-subtle)] ${className}`}
    >
      {tabs.map((tab) => {
        const selected = tab.id === active;
        return (
          <button
            key={tab.id}
            role="tab"
            type="button"
            aria-selected={selected}
            disabled={tab.disabled}
            onClick={() => onChange(tab.id)}
            className={`relative flex shrink-0 items-center gap-1.5 whitespace-nowrap px-3 py-2.5 text-[0.8125rem] font-medium transition-colors disabled:opacity-40 ${
              selected
                ? "text-[var(--text-primary)]"
                : "text-[var(--text-muted)] hover:text-[var(--text-secondary)]"
            }`}
          >
            {tab.label}
            {tab.count != null ? (
              <span
                className={`tabular rounded px-1 text-[0.6875rem] ${
                  selected
                    ? "bg-[var(--surface-inverse)] text-[var(--text-inverse)]"
                    : "bg-[var(--surface-3)] text-[var(--text-muted)]"
                }`}
              >
                {tab.count}
              </span>
            ) : null}
            {selected ? (
              <span className="absolute inset-x-2 -bottom-px h-0.5 rounded-full bg-[var(--surface-inverse)]" />
            ) : null}
          </button>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Drawer
// ---------------------------------------------------------------------------

/**
 * A right-hand panel for detail that would break the reading flow inline.
 *
 * <p>Used for the evidence a citation points at. That is the interaction the whole interface turns
 * on: a reader should be able to check a claim without losing their place in the argument, which is
 * what a drawer does and a navigation does not.
 */
export function Drawer({
  open,
  onClose,
  title,
  subtitle,
  children,
  width = "38rem",
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  width?: string;
}) {
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="no-print fixed inset-0 z-40 flex justify-end">
      <button
        type="button"
        aria-label="Close"
        onClick={onClose}
        className="absolute inset-0 bg-[color-mix(in_oklab,var(--surface-inverse)_45%,transparent)] backdrop-blur-[2px]"
      />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        style={{ width: `min(${width}, 100vw)` }}
        className="rise-in relative flex h-full flex-col border-l border-[var(--border-subtle)] bg-[var(--surface-1)] shadow-[var(--shadow-pop)]"
      >
        <header className="flex items-start justify-between gap-3 border-b border-[var(--border-subtle)] px-5 py-4">
          <div className="min-w-0">
            <h2 id={titleId} className="text-sm font-semibold text-[var(--text-primary)]">
              {title}
            </h2>
            {subtitle ? <div className="mt-1 text-xs text-[var(--text-muted)]">{subtitle}</div> : null}
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="Close panel">
            <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
              <path d="M2 2l10 10M12 2L2 12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </Button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
      </aside>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Disclosure
// ---------------------------------------------------------------------------

export function Disclosure({
  summary,
  children,
  defaultOpen = false,
  className = "",
}: {
  summary: ReactNode;
  children: ReactNode;
  defaultOpen?: boolean;
  className?: string;
}) {
  return (
    <details
      open={defaultOpen}
      className={`group rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-1)] ${className}`}
    >
      <summary className="flex cursor-pointer list-none items-center gap-2 px-3 py-2 text-xs font-medium text-[var(--text-secondary)] hover:text-[var(--text-primary)]">
        <svg
          viewBox="0 0 12 12"
          className="size-3 shrink-0 transition-transform group-open:rotate-90"
          aria-hidden="true"
        >
          <path d="M4 2l4 4-4 4" stroke="currentColor" strokeWidth="1.6" fill="none" strokeLinecap="round" />
        </svg>
        {summary}
      </summary>
      <div className="border-t border-[var(--border-subtle)] px-3 py-3">{children}</div>
    </details>
  );
}

// ---------------------------------------------------------------------------
// Field rendering
// ---------------------------------------------------------------------------

/**
 * A labelled field that is honest about being empty.
 *
 * <p>The fallback is not decoration. The pipeline asks the model for specific things — what happens
 * when the AI is wrong, who checks the output — and a step that did not answer should read as a gap
 * rather than as a field that quietly disappeared.
 */
export function Field({
  label,
  value,
  fallback = "Not stated",
  className = "",
}: {
  label: string;
  value: ReactNode;
  fallback?: string;
  className?: string;
}) {
  const empty = value == null || value === "";
  return (
    <div className={className}>
      <p className="eyebrow">{label}</p>
      <div
        className={`mt-1 text-[0.8125rem] leading-relaxed ${
          empty ? "italic text-[var(--text-muted)]" : "text-[var(--text-secondary)]"
        }`}
      >
        {empty ? fallback : value}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Forms
// ---------------------------------------------------------------------------

/** One input style, so every form in the application looks like the same application. */
export const INPUT_CLASSES =
  "w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-2)] px-3 py-2 text-sm " +
  "text-[var(--text-primary)] outline-none transition-colors placeholder:text-[var(--text-muted)] " +
  "focus:border-[var(--border-focus)] disabled:opacity-60";

/**
 * A labelled form control.
 *
 * <p>Distinct from {@link Field}, which displays a value that already exists. This one wraps an
 * input, wires the label to it, and puts the error where a screen reader will read it with the
 * field rather than somewhere else on the page.
 */
export function FormField({
  label,
  htmlFor,
  hint,
  error,
  required = false,
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
      <label htmlFor={htmlFor} className="mb-1 block text-xs font-medium text-[var(--text-secondary)]">
        {label}
        {required ? <span className="ml-0.5 text-[var(--status-critical-ink)]">*</span> : null}
      </label>
      {children}
      {error ? (
        <p id={`${htmlFor}-error`} role="alert" className="mt-1 text-xs text-[var(--status-critical-ink)]">
          {error}
        </p>
      ) : hint ? (
        <p className="mt-1 text-[0.6875rem] text-[var(--text-muted)]">{hint}</p>
      ) : null}
    </div>
  );
}

/** A centred dialog, for a choice that interrupts rather than one that sits alongside. */
export function Modal({
  open,
  onClose,
  title,
  children,
  width = "36rem",
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  width?: string;
}) {
  const titleId = useId();

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="no-print fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-4 sm:p-8">
      <button
        type="button"
        aria-label="Close"
        onClick={onClose}
        className="fixed inset-0 bg-[color-mix(in_oklab,var(--surface-inverse)_50%,transparent)] backdrop-blur-[2px]"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        style={{ width: `min(${width}, 100%)` }}
        className="rise-in relative my-auto rounded-[var(--radius-panel)] border border-[var(--border-subtle)] bg-[var(--surface-1)] shadow-[var(--shadow-pop)]"
      >
        <header className="flex items-center justify-between gap-3 border-b border-[var(--border-subtle)] px-5 py-3.5">
          <h2 id={titleId} className="text-sm font-semibold">
            {title}
          </h2>
          <Button variant="ghost" size="sm" onClick={onClose} aria-label="Close">
            <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
              <path d="M2 2l10 10M12 2L2 12" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
            </svg>
          </Button>
        </header>
        <div className="px-5 py-4">{children}</div>
      </div>
    </div>
  );
}

export function CopyButton({ value, label = "Copy" }: { value: string; label?: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <Button
      size="sm"
      variant="ghost"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setCopied(true);
          window.setTimeout(() => setCopied(false), 1600);
        } catch {
          // Clipboard access can be refused; the button simply does not confirm.
        }
      }}
    >
      {copied ? "Copied" : label}
    </Button>
  );
}

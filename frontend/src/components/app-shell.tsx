"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import { Badge, Button, Spinner } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth-context";
import type { ProcessSummary } from "@/lib/types";

/**
 * The frame every page sits in.
 *
 * <p>A sidebar rather than a top bar, because this application has two kinds of navigation that do
 * not fit on one line: a handful of places to go, and a list of processes to go to. The sidebar
 * holds both, and on a narrow screen it collapses into a sheet rather than being redesigned into
 * something else.
 */

interface NavItem {
  href: string;
  label: string;
  icon: ReactNode;
  description: string;
}

const NAV: NavItem[] = [
  {
    href: "/",
    label: "Processes",
    description: "Everything you can analyse",
    icon: (
      <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <rect x="2" y="2.5" width="5" height="5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
        <rect x="9" y="2.5" width="5" height="5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
        <rect x="2" y="9" width="5" height="4.5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
        <rect x="9" y="9" width="5" height="4.5" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
      </svg>
    ),
  },
  {
    href: "/evidence",
    label: "Evidence",
    description: "Every source the system can cite",
    icon: (
      <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path d="M3 2.5h6.5L13 6v7.5H3z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        <path d="M9.5 2.5V6H13" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" />
        <path d="M5.5 9h5M5.5 11h3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
      </svg>
    ),
  },
  {
    href: "/system",
    label: "Engine",
    description: "Models, budgets and connectors",
    icon: (
      <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <circle cx="8" cy="8" r="2.2" stroke="currentColor" strokeWidth="1.4" />
        <path
          d="M8 1.8v1.6M8 12.6v1.6M14.2 8h-1.6M3.4 8H1.8M12.4 3.6l-1.1 1.1M4.7 11.3l-1.1 1.1M12.4 12.4l-1.1-1.1M4.7 4.7L3.6 3.6"
          stroke="currentColor"
          strokeWidth="1.4"
          strokeLinecap="round"
        />
      </svg>
    ),
  },
  {
    href: "/how-it-works",
    label: "How it works",
    description: "The pipeline, stage by stage",
    icon: (
      <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <circle cx="8" cy="8" r="6" stroke="currentColor" strokeWidth="1.4" />
        <path d="M6.3 6.2A1.8 1.8 0 1 1 8 8.6v1" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
        <circle cx="8" cy="11.6" r="0.8" fill="currentColor" />
      </svg>
    ),
  },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { user, signOut } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [paletteOpen, setPaletteOpen] = useState(false);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setPaletteOpen((open) => !open);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const isActive = (href: string) => (href === "/" ? pathname === "/" : pathname.startsWith(href));

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[16rem_1fr]">
      {/* Sidebar, permanent on desktop */}
      <aside className="no-print hidden border-r border-[var(--border-subtle)] bg-[var(--surface-1)] lg:flex lg:h-screen lg:flex-col lg:sticky lg:top-0">
        <SidebarContent
          isActive={isActive}
          user={user?.displayName ?? null}
          onSignOut={signOut}
          onOpenPalette={() => setPaletteOpen(true)}
        />
      </aside>

      {/* Sidebar as a sheet, on narrow screens */}
      {mobileOpen ? (
        <div className="no-print fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close menu"
            onClick={() => setMobileOpen(false)}
            className="absolute inset-0 bg-[color-mix(in_oklab,var(--surface-inverse)_45%,transparent)]"
          />
          <aside className="rise-in relative flex h-full w-64 flex-col border-r border-[var(--border-subtle)] bg-[var(--surface-1)]">
            <SidebarContent
              isActive={isActive}
              user={user?.displayName ?? null}
              onSignOut={signOut}
              // Navigating is the only way out of the sheet other than dismissing it, so the links
              // close it themselves. Watching the pathname from an effect would do the same thing
              // one render later, for every visitor, on every page.
              onNavigate={() => setMobileOpen(false)}
              onOpenPalette={() => {
                setMobileOpen(false);
                setPaletteOpen(true);
              }}
            />
          </aside>
        </div>
      ) : null}

      <div className="flex min-w-0 flex-col">
        <header className="no-print sticky top-0 z-30 flex items-center gap-3 border-b border-[var(--border-subtle)] bg-[color-mix(in_oklab,var(--surface-page)_88%,transparent)] px-4 py-2.5 backdrop-blur lg:hidden">
          <Button variant="ghost" size="sm" onClick={() => setMobileOpen(true)} aria-label="Open menu">
            <svg viewBox="0 0 16 16" className="size-4" aria-hidden="true">
              <path d="M2 4h12M2 8h12M2 12h12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
            </svg>
          </Button>
          <span className="text-sm font-semibold">Future Process Designer</span>
          <div className="ml-auto">
            <ThemeToggle />
          </div>
        </header>

        <main id="main" className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
          {children}
        </main>
      </div>

      {paletteOpen ? <CommandPalette onClose={() => setPaletteOpen(false)} /> : null}
    </div>
  );
}

function SidebarContent({
  isActive,
  user,
  onSignOut,
  onOpenPalette,
  onNavigate,
}: {
  isActive: (href: string) => boolean;
  user: string | null;
  onSignOut: () => void;
  onOpenPalette: () => void;
  /** Set only by the narrow-screen sheet, which has to dismiss itself when a link is followed. */
  onNavigate?: () => void;
}) {
  return (
    <>
      <div className="flex items-center gap-2.5 px-4 py-4">
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
        <div className="min-w-0">
          <p className="truncate text-[0.8125rem] font-semibold leading-tight">Future Process Designer</p>
          <p className="truncate text-[0.6875rem] text-[var(--text-muted)]">AssessWise</p>
        </div>
      </div>

      <div className="px-3">
        <button
          type="button"
          onClick={onOpenPalette}
          className="flex w-full items-center gap-2 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-2)] px-2.5 py-1.5 text-left text-xs text-[var(--text-muted)] hover:border-[var(--border-strong)]"
        >
          <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
            <circle cx="6" cy="6" r="4" fill="none" stroke="currentColor" strokeWidth="1.4" />
            <path d="M9 9l3.5 3.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
          </svg>
          Search processes
          <kbd className="mono ml-auto rounded border border-[var(--border-subtle)] px-1 py-px text-[0.625rem]">
            ⌘K
          </kbd>
        </button>
      </div>

      <nav className="mt-4 flex-1 space-y-0.5 px-3">
        {NAV.map((item) => {
          const active = isActive(item.href);
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              aria-current={active ? "page" : undefined}
              className={`flex items-start gap-2.5 rounded-lg px-2.5 py-2 transition-colors ${
                active
                  ? "bg-[var(--surface-3)] text-[var(--text-primary)]"
                  : "text-[var(--text-secondary)] hover:bg-[var(--surface-3)]"
              }`}
            >
              <span className={`mt-0.5 size-4 shrink-0 ${active ? "text-[var(--text-link)]" : ""}`}>
                {item.icon}
              </span>
              <span className="min-w-0">
                <span className="block text-[0.8125rem] font-medium leading-tight">{item.label}</span>
                <span className="block truncate text-[0.6875rem] text-[var(--text-muted)]">
                  {item.description}
                </span>
              </span>
            </Link>
          );
        })}
      </nav>

      <div className="mt-4 px-3 pb-3">
        <Link
          href="/processes/new"
          onClick={onNavigate}
          className="flex w-full items-center justify-center gap-1.5 rounded-lg bg-[var(--surface-inverse)] px-3 py-2 text-[0.8125rem] font-medium text-[var(--text-inverse)] hover:opacity-90"
        >
          <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
            <path d="M7 2.5v9M2.5 7h9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
          </svg>
          New process
        </Link>
      </div>

      <div className="border-t border-[var(--border-subtle)] px-3 py-3">
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate text-xs font-medium text-[var(--text-secondary)]">{user ?? "Signed out"}</p>
            <button
              type="button"
              onClick={onSignOut}
              className="text-[0.6875rem] text-[var(--text-muted)] hover:text-[var(--text-primary)]"
            >
              Sign out
            </button>
          </div>
          <ThemeToggle />
        </div>
      </div>
    </>
  );
}

type ThemeChoice = "light" | "dark" | "system";

const THEME_KEY = "afpd.theme";
const themeListeners = new Set<() => void>();

/**
 * The chosen theme lives in `localStorage` and on the root element, not in React state.
 *
 * <p>It has to: a script in the document head applies it before the first paint, so React is not
 * the source of truth and cannot be. Treating it as an external store — rather than as state
 * initialised by an effect — also means a change in another tab is picked up, and means the toggle
 * renders the real value on its first paint instead of "system" followed by a correction.
 */
function readTheme(): ThemeChoice {
  try {
    const stored = window.localStorage.getItem(THEME_KEY);
    return stored === "light" || stored === "dark" ? stored : "system";
  } catch {
    // Private browsing, or site data blocked. The pre-paint script fails the same way, silently.
    return "system";
  }
}

function subscribeTheme(listener: () => void) {
  themeListeners.add(listener);
  window.addEventListener("storage", listener);
  return () => {
    themeListeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

function writeTheme(next: ThemeChoice) {
  if (next === "system") {
    delete document.documentElement.dataset.theme;
  } else {
    document.documentElement.dataset.theme = next;
  }
  try {
    if (next === "system") window.localStorage.removeItem(THEME_KEY);
    else window.localStorage.setItem(THEME_KEY, next);
  } catch {
    // The theme still applies for this page; it just will not be remembered.
  }
  themeListeners.forEach((listener) => listener());
}

/**
 * Light, dark, or whatever the operating system says.
 *
 * <p>Three states rather than two. A binary toggle has to pick a side at first load and is wrong
 * for whoever set a system preference; "system" is the honest default and the other two are an
 * override that persists.
 */
export function ThemeToggle() {
  const theme = useSyncExternalStore(subscribeTheme, readTheme, () => "system" as ThemeChoice);
  const apply = writeTheme;

  const next = theme === "system" ? "light" : theme === "light" ? "dark" : "system";
  const label = { system: "Match system", light: "Light", dark: "Dark" }[theme];

  return (
    <button
      type="button"
      onClick={() => apply(next)}
      title={`Theme: ${label}. Click for ${next === "system" ? "system" : next}.`}
      aria-label={`Theme: ${label}`}
      className="flex size-7 items-center justify-center rounded-lg border border-[var(--border-subtle)] text-[var(--text-secondary)] hover:bg-[var(--surface-3)]"
    >
      {theme === "dark" ? (
        <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
          <path
            d="M11.5 8.6A5 5 0 0 1 5.4 2.5a5 5 0 1 0 6.1 6.1z"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            strokeLinejoin="round"
          />
        </svg>
      ) : theme === "light" ? (
        <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
          <circle cx="7" cy="7" r="2.6" fill="none" stroke="currentColor" strokeWidth="1.4" />
          <path
            d="M7 1.2v1.4M7 11.4v1.4M12.8 7h-1.4M2.6 7H1.2M11.1 2.9l-1 1M3.9 10.1l-1 1M11.1 11.1l-1-1M3.9 3.9l-1-1"
            stroke="currentColor"
            strokeWidth="1.3"
            strokeLinecap="round"
          />
        </svg>
      ) : (
        <svg viewBox="0 0 14 14" className="size-3.5" aria-hidden="true">
          <rect x="1.5" y="3" width="11" height="7.5" rx="1.2" fill="none" stroke="currentColor" strokeWidth="1.3" />
          <path d="M5 12.2h4" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" />
        </svg>
      )}
    </button>
  );
}

/**
 * Jump straight to a process by name.
 *
 * <p>Worth the keystroke handler because the demo it is built for is a live one: a judge asks to
 * see a particular process, and hunting for it in a grid while somebody watches is exactly the
 * moment an interface should get out of the way.
 */
function CommandPalette({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ProcessSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [highlighted, setHighlighted] = useState(0);

  const search = useCallback(async (term: string) => {
    setLoading(true);
    try {
      const page = await api.listProcesses({ q: term, size: 8, sort: "recent" });
      setResults(page.items);
      setHighlighted(0);
    } catch {
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => search(query), query ? 200 : 0);
    return () => window.clearTimeout(timer);
  }, [query, search]);

  const go = useCallback(
    (id: string) => {
      router.push(`/processes/${id}`);
      onClose();
    },
    [router, onClose],
  );

  const onKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Escape") onClose();
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setHighlighted((index) => Math.min(index + 1, results.length - 1));
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      setHighlighted((index) => Math.max(index - 1, 0));
    }
    if (event.key === "Enter" && results[highlighted]) {
      go(results[highlighted].id);
    }
  };

  const hint = useMemo(
    () => (query ? `${results.length} match${results.length === 1 ? "" : "es"}` : "Recent processes"),
    [query, results.length],
  );

  return (
    <div className="no-print fixed inset-0 z-50 flex items-start justify-center px-4 pt-[12vh]">
      <button
        type="button"
        aria-label="Close search"
        onClick={onClose}
        className="absolute inset-0 bg-[color-mix(in_oklab,var(--surface-inverse)_50%,transparent)] backdrop-blur-[2px]"
      />
      <div className="rise-in relative w-full max-w-lg overflow-hidden rounded-[var(--radius-panel)] border border-[var(--border-subtle)] bg-[var(--surface-1)] shadow-[var(--shadow-pop)]">
        <div className="flex items-center gap-2 border-b border-[var(--border-subtle)] px-3">
          <svg viewBox="0 0 14 14" className="size-4 text-[var(--text-muted)]" aria-hidden="true">
            <circle cx="6" cy="6" r="4" fill="none" stroke="currentColor" strokeWidth="1.4" />
            <path d="M9 9l3.5 3.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
          </svg>
          <input
            autoFocus
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Search processes…"
            aria-label="Search processes"
            className="w-full bg-transparent py-3 text-sm outline-none placeholder:text-[var(--text-muted)]"
          />
          {loading ? <Spinner className="size-3.5 text-[var(--text-muted)]" /> : null}
        </div>

        <p className="eyebrow px-3 py-2">{hint}</p>

        <ul className="max-h-72 overflow-y-auto pb-2">
          {results.map((process, index) => (
            <li key={process.id}>
              <button
                type="button"
                onMouseEnter={() => setHighlighted(index)}
                onClick={() => go(process.id)}
                className={`flex w-full items-center gap-2 px-3 py-2 text-left ${
                  index === highlighted ? "bg-[var(--surface-3)]" : ""
                }`}
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[0.8125rem] font-medium text-[var(--text-primary)]">
                    {process.name}
                  </span>
                  <span className="block truncate text-[0.6875rem] text-[var(--text-muted)]">
                    {process.industry}
                  </span>
                </span>
                <Badge tone={process.status === "ANALYZED" ? "good" : "neutral"}>
                  {process.status === "ANALYZED" ? "Analysed" : "Not run"}
                </Badge>
              </button>
            </li>
          ))}
          {!loading && results.length === 0 ? (
            <li className="px-3 py-6 text-center text-xs text-[var(--text-muted)]">
              Nothing matched “{query}”.
            </li>
          ) : null}
        </ul>
      </div>
    </div>
  );
}

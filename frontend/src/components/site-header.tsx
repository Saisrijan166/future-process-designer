"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth-context";

const NAV = [
  { href: "/", label: "Processes" },
  { href: "/how-it-works", label: "How it works" },
  { href: "/evidence", label: "Evidence" },
];

export function SiteHeader() {
  const pathname = usePathname();
  const { user } = useAuth();

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" || pathname.startsWith("/processes") : pathname === href;

  return (
    <header className="sticky top-0 z-40 border-b border-ink-200 bg-white/85 backdrop-blur-md">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6 lg:px-8">
        <Link href={user ? "/" : "/login"} className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className="grid size-9 place-items-center rounded-xl bg-ink-900 text-white"
          >
            <svg className="size-5" viewBox="0 0 20 20" fill="none">
              <path d="M3 5.5h5M3 10h9M3 14.5h5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
              <path
                d="M14.5 3.5l.9 2.4 2.4.9-2.4.9-.9 2.4-.9-2.4-2.4-.9 2.4-.9.9-2.4Z"
                fill="currentColor"
              />
            </svg>
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

        {user ? (
          <nav className="ml-auto flex items-center gap-1">
            {NAV.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                aria-current={isActive(item.href) ? "page" : undefined}
                className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  isActive(item.href)
                    ? "bg-ink-100 text-ink-900"
                    : "text-ink-600 hover:bg-ink-50 hover:text-ink-900"
                }`}
              >
                {item.label}
              </Link>
            ))}
            <Link
              href="/processes/new"
              className="ml-1 rounded-lg bg-ink-900 px-3.5 py-2 text-sm font-medium text-white transition-colors hover:bg-ink-800"
            >
              + New process
            </Link>
            <AccountMenu />
          </nav>
        ) : null}
      </div>
    </header>
  );
}

/** Who you are signed in as, and the way out. */
function AccountMenu() {
  const { user, signOut } = useAuth();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // Close on an outside click or Escape — the two things a user expects of a dropdown.
  useEffect(() => {
    if (!open) return;

    function onPointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") setOpen(false);
    }

    document.addEventListener("mousedown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  if (!user) return null;

  const initials = user.displayName.trim().charAt(0).toUpperCase() || "?";

  return (
    <div ref={containerRef} className="relative ml-1">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="grid size-9 place-items-center rounded-full bg-ink-100 text-sm font-semibold text-ink-700 transition-colors hover:bg-ink-200"
        title={user.email}
      >
        {initials}
      </button>

      {open ? (
        <div
          role="menu"
          className="absolute right-0 z-50 mt-2 w-60 rounded-xl border border-ink-200 bg-white p-1.5 shadow-lg"
        >
          <div className="border-b border-ink-100 px-3 py-2">
            <p className="truncate text-sm font-medium text-ink-900">{user.displayName}</p>
            <p className="truncate text-xs text-ink-500">{user.email}</p>
          </div>
          <p className="px-3 py-2 text-xs leading-relaxed text-ink-500">
            Processes you create are private to this account. The samples are shared with everyone.
          </p>
          <button
            type="button"
            role="menuitem"
            onClick={() => {
              setOpen(false);
              signOut();
            }}
            className="w-full rounded-lg px-3 py-2 text-left text-sm font-medium text-ink-700 transition-colors hover:bg-ink-100"
          >
            Sign out
          </button>
        </div>
      ) : null}
    </div>
  );
}

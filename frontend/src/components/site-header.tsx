"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV = [
  { href: "/", label: "Processes" },
  { href: "/how-it-works", label: "How it works" },
  { href: "/evidence", label: "Evidence" },
];

export function SiteHeader() {
  const pathname = usePathname();

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" || pathname.startsWith("/processes") : pathname === href;

  return (
    <header className="sticky top-0 z-40 border-b border-ink-200 bg-white/85 backdrop-blur-md">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6 lg:px-8">
        <Link href="/" className="flex items-center gap-2.5">
          <span
            aria-hidden="true"
            className="grid size-9 place-items-center rounded-xl bg-ink-900 text-white"
          >
            <svg className="size-5" viewBox="0 0 20 20" fill="none">
              <path
                d="M3 5.5h5M3 10h9M3 14.5h5"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
              />
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
        </nav>
      </div>
    </header>
  );
}

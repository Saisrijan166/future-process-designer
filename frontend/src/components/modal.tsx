"use client";

import { useEffect, useRef } from "react";
import type { ReactNode } from "react";

/**
 * A dialog built on the native `<dialog>` element.
 *
 * Using the platform element rather than a div-with-a-high-z-index is what makes this correct
 * without a pile of custom code: `showModal()` brings a focus trap, Escape-to-close, inertness for
 * the rest of the page, and top-layer stacking that no ancestor's `overflow` or `z-index` can
 * break. The only things left to add are backdrop-click-to-close and locking background scroll,
 * which the element does not cover.
 */
export function Modal({
  open,
  onClose,
  title,
  description,
  children,
  labelledBy = "modal-title",
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  labelledBy?: string;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  // `showModal()` makes the background inert but does not stop it scrolling behind the dialog.
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={labelledBy}
      onClose={onClose}
      // The backdrop is painted by the dialog itself, so a click on it lands on this element
      // rather than on any child — which is exactly how to tell the two apart.
      onClick={(event) => {
        if (event.target === dialogRef.current) onClose();
      }}
      className="m-auto w-[min(56rem,calc(100vw-2rem))] rounded-2xl border border-ink-200 bg-white p-0 text-ink-800 shadow-2xl backdrop:bg-ink-950/50 backdrop:backdrop-blur-[2px]"
    >
      <div className="flex max-h-[85vh] flex-col">
        <header className="flex items-start justify-between gap-4 border-b border-ink-200 px-5 py-4">
          <div>
            <h2 id={labelledBy} className="text-base font-semibold text-ink-900">
              {title}
            </h2>
            {description ? (
              <p className="mt-1 max-w-2xl text-sm leading-relaxed text-ink-600">{description}</p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="-mt-1 -mr-1 grid size-8 shrink-0 place-items-center rounded-lg text-ink-500 transition-colors hover:bg-ink-100 hover:text-ink-900"
          >
            <svg className="size-4" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path
                d="M4 4l8 8M12 4l-8 8"
                stroke="currentColor"
                strokeWidth="1.7"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </header>

        <div className="overflow-y-auto p-5">{children}</div>
      </div>
    </dialog>
  );
}

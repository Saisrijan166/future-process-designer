"use client";

import { useCallback, useEffect, useState } from "react";
import type { ApiError } from "./api";

interface ResourceState<T> {
  data: T | null;
  error: ApiError | null;
  loading: boolean;
}

/**
 * Loads a value from the API once per mount, with retry and a cancellation guard.
 *
 * <p>State is only ever set from inside the promise callbacks, never synchronously in the effect
 * body — that avoids the cascading re-render the React compiler warns about, and the `cancelled`
 * flag stops a slow response from writing into a component that has already navigated away.
 *
 * @param load must be a stable reference (wrap it in `useCallback`), or the fetch will loop.
 */
export function useApiResource<T>(load: () => Promise<T>): ResourceState<T> & {
  reload: () => void;
  replace: (data: T) => void;
} {
  const [state, setState] = useState<ResourceState<T>>({ data: null, error: null, loading: true });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    load()
      .then((data) => {
        if (!cancelled) setState({ data, error: null, loading: false });
      })
      .catch((error: unknown) => {
        if (!cancelled) setState({ data: null, error: error as ApiError, loading: false });
      });
    return () => {
      cancelled = true;
    };
  }, [load, attempt]);

  const reload = useCallback(() => setAttempt((value) => value + 1), []);
  const replace = useCallback((data: T) => setState({ data, error: null, loading: false }), []);

  return { ...state, reload, replace };
}

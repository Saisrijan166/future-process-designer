"use client";

import { useRouter } from "next/navigation";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, auth } from "@/lib/api";
import type { AuthUser } from "@/lib/types";

interface AuthState {
  user: AuthUser | null;
  /** True until the stored token has been checked, so pages do not flash the sign-in screen. */
  initialising: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, displayName?: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Holds the signed-in account for the whole app.
 *
 * <p>On load it restores the token from localStorage and asks the API who it belongs to, rather
 * than trusting the token's contents. That one round trip is what makes an expired or revoked
 * session show the sign-in screen immediately instead of failing on the first real request.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initialising, setInitialising] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const token = auth.restoreToken();

    if (!token) {
      // No stored session. Deferred to a microtask so no state is set during the effect body.
      Promise.resolve().then(() => {
        if (!cancelled) setInitialising(false);
      });
      return () => {
        cancelled = true;
      };
    }

    api
      .me()
      .then((account) => {
        if (!cancelled) {
          setUser(account);
          setInitialising(false);
        }
      })
      .catch(() => {
        // The token is expired, revoked, or was signed with a different key.
        auth.setToken(null);
        if (!cancelled) {
          setUser(null);
          setInitialising(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // A 401 on any request means the session died mid-use; drop it and return to sign-in.
  useEffect(() => {
    auth.onUnauthorized(() => {
      setUser(null);
      router.replace("/login");
    });
    return () => auth.onUnauthorized(null);
  }, [router]);

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await api.login({ email, password });
    auth.setToken(response.token);
    setUser(response.user);
  }, []);

  const signUp = useCallback(async (email: string, password: string, displayName?: string) => {
    const response = await api.register({ email, password, displayName });
    auth.setToken(response.token);
    setUser(response.user);
  }, []);

  const signOut = useCallback(() => {
    // Nothing to revoke server-side: the token is stateless and short-lived by design.
    auth.setToken(null);
    setUser(null);
    router.replace("/login");
  }, [router]);

  const value = useMemo(
    () => ({ user, initialising, signIn, signUp, signOut }),
    [user, initialising, signIn, signUp, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return context;
}

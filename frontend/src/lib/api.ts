import type {
  AnalysisResult,
  AuthResponse,
  AuthUser,
  AnalysisRunTrace,
  Comparison,
  CreateProcessRequest,
  KnowledgeSnippet,
  ProcessDetail,
  ProcessListQuery,
  ProcessPage,
  Role,
  SystemTool,
} from "./types";

/**
 * Typed client for the Spring Boot API.
 *
 * <p>Everything is fetched from the browser rather than rendered on the server. That is a
 * deliberate choice for this deployment: the backend runs on a free Render instance that cold-
 * starts in roughly a minute, and a server-rendered page would simply time out. Fetching client
 * side lets the UI show "waking the backend up…" instead of a 504.
 */
export const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");

/** Generous, because a free-tier backend may be cold and the model call itself takes ~10-40s. */
const DEFAULT_TIMEOUT_MS = 60_000;
const ANALYZE_TIMEOUT_MS = 240_000;

/**
 * The bearer token for the current session.
 *
 * Held in a module variable and mirrored to localStorage: the variable is what every request
 * reads, so a sign-out takes effect immediately even for a request already in flight, while
 * localStorage is what survives a page reload.
 */
const TOKEN_STORAGE_KEY = "afpd.token";
let authToken: string | null = null;

/** Called when a request comes back 401, so the app can send the user back to sign in. */
let onUnauthorized: (() => void) | null = null;

export const auth = {
  setToken(token: string | null) {
    authToken = token;
    if (typeof window === "undefined") return;
    if (token) {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  },

  /** Reads the stored token back after a reload. */
  restoreToken(): string | null {
    if (typeof window === "undefined") return null;
    authToken = window.localStorage.getItem(TOKEN_STORAGE_KEY);
    return authToken;
  },

  token: () => authToken,

  onUnauthorized(handler: (() => void) | null) {
    onUnauthorized = handler;
  },
};

/** RFC 7807 problem document, as produced by GlobalExceptionHandler. */
interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  reason?: string;
  retryAfterSeconds?: number;
  errors?: Record<string, string>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail, fallback: string) {
    super(problem.detail || problem.title || fallback);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  /** Field-level validation messages, when the failure was a 400 from bean validation. */
  get fieldErrors(): Record<string, string> {
    return this.problem.errors ?? {};
  }

  /** Extra context the backend attached, e.g. why the model output was rejected. */
  get reason(): string | undefined {
    return this.problem.reason;
  }
}

async function request<T>(path: string, init: RequestInit = {}, timeoutMs = DEFAULT_TIMEOUT_MS): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...(authToken ? { Authorization: `Bearer ${authToken}` } : {}),
        ...init.headers,
      },
      signal: AbortSignal.timeout(timeoutMs),
      cache: "no-store",
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "TimeoutError") {
      throw new ApiError(
        408,
        { title: "Request timed out" },
        `The backend did not respond within ${Math.round(timeoutMs / 1000)}s.`,
      );
    }
    throw new ApiError(
      0,
      { title: "Cannot reach the backend" },
      `Could not reach the API at ${API_BASE_URL}. If it is deployed on a free tier it may be starting up — wait a moment and retry.`,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let body: unknown = undefined;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = undefined;
    }
  }

  if (!response.ok) {
    const problem: ProblemDetail =
      body && typeof body === "object" ? (body as ProblemDetail) : { title: response.statusText };

    // An expired or invalid token: drop it and let the app show the sign-in screen, rather than
    // leaving the user staring at a permission error they cannot act on.
    if (response.status === 401 && !path.startsWith("/api/auth/")) {
      auth.setToken(null);
      onUnauthorized?.();
    }

    throw new ApiError(response.status, problem, `Request failed with status ${response.status}.`);
  }

  return body as T;
}

export const api = {
  register: (payload: { email: string; password: string; displayName?: string }) =>
    request<AuthResponse>("/api/auth/register", { method: "POST", body: JSON.stringify(payload) }),

  login: (payload: { email: string; password: string }) =>
    request<AuthResponse>("/api/auth/login", { method: "POST", body: JSON.stringify(payload) }),

  me: () => request<AuthUser>("/api/auth/me", {}, 15_000),

  listProcesses: (query: ProcessListQuery = {}) => {
    const params = new URLSearchParams();
    if (query.page != null) params.set("page", String(query.page));
    if (query.size != null) params.set("size", String(query.size));
    if (query.status) params.set("status", query.status);
    if (query.q?.trim()) params.set("q", query.q.trim());
    if (query.sort) params.set("sort", query.sort);
    const suffix = params.toString();
    return request<ProcessPage>(`/api/processes${suffix ? `?${suffix}` : ""}`);
  },

  getProcess: (id: string) => request<ProcessDetail>(`/api/processes/${id}`),

  getComparison: (id: string) => request<Comparison>(`/api/processes/${id}/comparison`),

  createProcess: (payload: CreateProcessRequest) =>
    request<ProcessDetail>("/api/processes", { method: "POST", body: JSON.stringify(payload) }),

  deleteProcess: (id: string) => request<void>(`/api/processes/${id}`, { method: "DELETE" }),

  analyze: (id: string) =>
    request<AnalysisResult>(`/api/processes/${id}/analyze`, { method: "POST" }, ANALYZE_TIMEOUT_MS),

  getLatestTrace: (id: string) =>
    request<AnalysisRunTrace>(`/api/processes/${id}/analysis-runs/latest/trace`),

  listKnowledgeSnippets: () => request<KnowledgeSnippet[]>("/api/knowledge-snippets"),

  listRoles: () => request<Role[]>("/api/roles"),

  listSystems: () => request<SystemTool[]>("/api/systems"),

  health: () => request<{ status: string }>("/actuator/health", {}, 10_000),
};

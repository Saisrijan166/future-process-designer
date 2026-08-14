import type {
  AutomationPotential,
  InterventionType,
  ResponsibilityType,
  Severity,
  SourceType,
} from "./types";

/** Dates are shown in DD-MM-YYYY, matching the convention used across the rest of the business. */
export function formatDate(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  const day = String(date.getDate()).padStart(2, "0");
  const month = String(date.getMonth() + 1).padStart(2, "0");
  return `${day}-${month}-${date.getFullYear()}`;
}

export function formatDateTime(value?: string | null): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  const time = date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit" });
  return `${formatDate(value)} ${time}`;
}

export function formatDuration(ms?: number | null): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

export function formatNumber(value?: number | null): string {
  return value == null ? "—" : value.toLocaleString("en-IN");
}

/** Turns SCREAMING_SNAKE enum values into readable labels. */
export function humanise(value: string): string {
  return value
    .toLowerCase()
    .split("_")
    .map((word) => (word === "ai" ? "AI" : word.charAt(0).toUpperCase() + word.slice(1)))
    .join(" ");
}

export type Tone = "neutral" | "info" | "success" | "warning" | "danger" | "accent";

export const severityTone: Record<Severity, Tone> = {
  LOW: "neutral",
  MEDIUM: "warning",
  HIGH: "danger",
};

export const automationTone: Record<AutomationPotential, Tone> = {
  LOW: "neutral",
  MEDIUM: "info",
  HIGH: "success",
};

export const responsibilityTone: Record<ResponsibilityType, Tone> = {
  AI_AUTOMATED: "accent",
  AI_AUGMENTED: "info",
  HUMAN_LED: "neutral",
};

export const interventionTone: Record<InterventionType, Tone> = {
  AUTOMATE: "accent",
  AUGMENT: "info",
  ELIMINATE: "danger",
  NEW: "success",
};

export const sourceTypeTone: Record<SourceType, Tone> = {
  LAW: "danger",
  GUIDANCE: "info",
  STANDARD: "accent",
  RESEARCH: "success",
  VENDOR: "warning",
  GENERAL_WEB: "neutral",
};

export const sourceTypeLabel: Record<SourceType, string> = {
  LAW: "Law",
  GUIDANCE: "Guidance",
  STANDARD: "Standard",
  RESEARCH: "Research",
  VENDOR: "Vendor",
  GENERAL_WEB: "General web",
};

export function hostnameOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

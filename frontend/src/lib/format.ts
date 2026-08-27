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

/**
 * Re-exported from the UI kit so there is exactly one tone vocabulary.
 *
 * <p>There used to be two — a set here and a set in the components — which is how a badge ended up
 * asking for a colour that did not exist. Importing rather than redeclaring makes that a compile
 * error instead of a rendering surprise.
 */
export type { Tone } from "@/components/ui";
import type { Tone } from "@/components/ui";

/**
 * Tones for the enum values, in one place.
 *
 * <p>Status tones are reserved and never reused as a category colour, so a severity and a source
 * type that both read "critical red" would be a bug rather than a coincidence — these maps are
 * where that rule is kept.
 */
export const severityTone: Record<Severity, Tone> = {
  LOW: "neutral",
  MEDIUM: "warning",
  HIGH: "critical",
};

export const automationTone: Record<AutomationPotential, Tone> = {
  LOW: "neutral",
  MEDIUM: "info",
  HIGH: "brand",
};

export const responsibilityTone: Record<ResponsibilityType, Tone> = {
  AI_AUTOMATED: "brand",
  AI_AUGMENTED: "info",
  HUMAN_LED: "neutral",
};

export const interventionTone: Record<InterventionType, Tone> = {
  AUTOMATE: "brand",
  AUGMENT: "info",
  ELIMINATE: "warning",
  NEW: "good",
};

export const sourceTypeTone: Record<SourceType, Tone> = {
  LAW: "brand",
  GUIDANCE: "info",
  STANDARD: "info",
  RESEARCH: "good",
  NEWS: "neutral",
  ENCYCLOPEDIA: "neutral",
  PRACTITIONER: "neutral",
  VENDOR: "warning",
  GENERAL_WEB: "neutral",
};

export const sourceTypeLabel: Record<SourceType, string> = {
  LAW: "Law",
  GUIDANCE: "Official guidance",
  STANDARD: "Standard",
  RESEARCH: "Research",
  NEWS: "News",
  ENCYCLOPEDIA: "Encyclopedia",
  PRACTITIONER: "Practitioner",
  VENDOR: "Vendor",
  GENERAL_WEB: "Web page",
};

export function hostnameOf(url: string): string {
  try {
    return new URL(url).hostname.replace(/^www\./, "");
  } catch {
    return url;
  }
}

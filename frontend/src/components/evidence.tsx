"use client";

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { Badge, Drawer, Field, Panel, type Tone } from "@/components/ui";
import { formatDate } from "@/lib/format";
import type { EvidenceClaim, FetchStatus, ResearchSource, SourceType } from "@/lib/types";

/**
 * The citation layer.
 *
 * <p>Everything here exists to answer one question quickly: <em>says who?</em> A generated
 * recommendation carries small numbered chips; clicking one opens the exact quote, the page it came
 * from, whether that quote was found in the page, and how the source scored — without leaving the
 * recommendation.
 *
 * <p>The distinction the whole layer is built around is verified versus unverified. A verified quote
 * was located in the stored page text by string matching; an unverified one was not, which usually
 * means the page could not be read or the model paraphrased. Both are shown. They never look the
 * same, because a citation that cannot be checked is worse than no citation if it is presented as
 * though it could be.
 */

// ---------------------------------------------------------------------------
// Presentation of source metadata
// ---------------------------------------------------------------------------

export const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  LAW: "Law",
  GUIDANCE: "Official guidance",
  STANDARD: "Standard",
  RESEARCH: "Research",
  VENDOR: "Vendor",
  NEWS: "News",
  ENCYCLOPEDIA: "Encyclopedia",
  PRACTITIONER: "Practitioner",
  GENERAL_WEB: "Web page",
};

/**
 * A colour per source type, from the categorical slots in fixed order.
 *
 * <p>Fixed assignment, never cycled: a filter that removes news must not repaint research. The
 * order follows the credibility hierarchy, so the strongest sources take the leading slots.
 */
export const SOURCE_TYPE_COLOURS: Record<SourceType, string> = {
  LAW: "var(--cat-1)",
  GUIDANCE: "var(--cat-2)",
  STANDARD: "var(--cat-3)",
  RESEARCH: "var(--cat-4)",
  NEWS: "var(--cat-5)",
  ENCYCLOPEDIA: "var(--cat-6)",
  PRACTITIONER: "var(--cat-7)",
  VENDOR: "var(--cat-8)",
  GENERAL_WEB: "var(--text-muted)",
};

export const FETCH_STATUS_TEXT: Record<FetchStatus, { label: string; tone: Tone; note: string }> = {
  FETCHED: { label: "Read in full", tone: "good", note: "The page was fetched and read directly." },
  READER_FALLBACK: {
    label: "Read via reader",
    tone: "good",
    note: "The publisher refused a direct request, so the text was obtained through a reader service.",
  },
  SNIPPET_ONLY: {
    label: "Snippet only",
    tone: "warning",
    note: "Only the search result summary was available, so quotes are limited to it.",
  },
  BLOCKED: {
    label: "Blocked",
    tone: "warning",
    note: "The publisher blocks automated readers. Nothing could be quoted from the body.",
  },
  SKIPPED: {
    label: "Not fetched",
    tone: "neutral",
    note: "The site's robots.txt asks automated clients not to read this path.",
  },
  FAILED: { label: "Unreadable", tone: "critical", note: "The page could not be read." },
  PENDING: { label: "Not attempted", tone: "neutral", note: "This source was found but not read." },
};

export function credibilityTone(score: number): Tone {
  if (score >= 70) return "good";
  if (score >= 45) return "info";
  if (score >= 25) return "warning";
  return "critical";
}

// ---------------------------------------------------------------------------
// The drawer, provided app-wide
// ---------------------------------------------------------------------------

interface EvidenceContextValue {
  open: (claims: EvidenceClaim[], focusIndex?: number) => void;
  openSource: (source: ResearchSource, claims: EvidenceClaim[]) => void;
}

const EvidenceContext = createContext<EvidenceContextValue | null>(null);

/**
 * Holds the evidence drawer for the whole page.
 *
 * <p>One drawer rather than one per component: citations appear inside recommendations, risks,
 * problems and the research view, and each of those rendering its own panel would mean four
 * implementations of the same thing and the possibility of two being open at once.
 */
export function EvidenceProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{
    claims: EvidenceClaim[];
    focus: number;
    source: ResearchSource | null;
  } | null>(null);

  const open = useCallback((claims: EvidenceClaim[], focusIndex = 0) => {
    if (claims.length === 0) return;
    setState({ claims, focus: focusIndex, source: null });
  }, []);

  const openSource = useCallback((source: ResearchSource, claims: EvidenceClaim[]) => {
    setState({ claims, focus: 0, source });
  }, []);

  const value = useMemo(() => ({ open, openSource }), [open, openSource]);

  return (
    <EvidenceContext.Provider value={value}>
      {children}
      <Drawer
        open={state != null}
        onClose={() => setState(null)}
        title={state?.source ? state.source.title : "Evidence"}
        subtitle={
          state?.source
            ? `${state.source.domain} · ${SOURCE_TYPE_LABELS[state.source.sourceType]}`
            : state
              ? `${state.claims.length} claim${state.claims.length === 1 ? "" : "s"} cited here`
              : undefined
        }
      >
        {state?.source ? <SourceDetail source={state.source} /> : null}
        <div className="space-y-3">
          {state?.claims.map((claim) => (
            <ClaimCard key={claim.id} claim={claim} showSource={!state.source} />
          ))}
          {state && state.claims.length === 0 ? (
            <p className="text-sm text-[var(--text-muted)]">
              No claims were extracted from this source. It is kept in the run because it was found
              and considered, which is worth knowing.
            </p>
          ) : null}
        </div>
      </Drawer>
    </EvidenceContext.Provider>
  );
}

export function useEvidence() {
  const context = useContext(EvidenceContext);
  if (!context) {
    return { open: () => {}, openSource: () => {} };
  }
  return context;
}

// ---------------------------------------------------------------------------
// Citation chips
// ---------------------------------------------------------------------------

/**
 * The numbered markers beside a generated statement.
 *
 * <p>Unverified citations are styled differently rather than hidden. Hiding them would make a
 * recommendation look better supported than it is, which is the exact failure this whole layer
 * exists to prevent.
 */
export function CitationChips({
  claims,
  className = "",
}: {
  claims: EvidenceClaim[];
  className?: string;
}) {
  const evidence = useEvidence();
  if (claims.length === 0) {
    return null;
  }
  return (
    <span className={`inline-flex flex-wrap items-center gap-0.5 ${className}`}>
      {claims.map((claim, index) => (
        <button
          key={claim.id}
          type="button"
          onClick={() => evidence.open(claims, index)}
          className={`citation ${claim.quoteVerified ? "" : "citation-unverified"}`}
          title={
            claim.quoteVerified
              ? `${claim.source.domain} — quote verified against the page`
              : `${claim.source.domain} — quote could NOT be found in the page`
          }
          aria-label={`Citation ${claim.citationIndex} from ${claim.source.domain}${
            claim.quoteVerified ? ", verified" : ", unverified"
          }`}
        >
          {claim.citationIndex}
        </button>
      ))}
    </span>
  );
}

/** The one-line verdict on whether a recommendation rests on anything checkable. */
export function GroundingBadge({ score, claimCount }: { score: number; claimCount: number }) {
  if (claimCount === 0) {
    return (
      <Badge tone="warning" title="No evidence from this run supports this. It may still be a good idea.">
        Ungrounded
      </Badge>
    );
  }
  if (score === 0) {
    return (
      <Badge tone="warning" title="The citations could not be verified against their sources.">
        Unverified sources
      </Badge>
    );
  }
  return (
    <Badge
      tone={score >= 60 ? "good" : "info"}
      title="Computed from the quote-verified claims cited, their source credibility and how many independent domains agree."
    >
      Grounding {score}/100
    </Badge>
  );
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

export function ClaimCard({ claim, showSource = true }: { claim: EvidenceClaim; showSource?: boolean }) {
  return (
    <Panel quiet className="p-3.5">
      <div className="flex items-start justify-between gap-2">
        <span className="citation shrink-0">{claim.citationIndex}</span>
        <div className="min-w-0 flex-1">
          <p className="text-[0.8125rem] font-medium leading-relaxed text-[var(--text-primary)]">
            {claim.claimText}
          </p>

          <blockquote className={`mt-2 ${claim.quoteVerified ? "quote" : "quote quote-unverified"}`}>
            &ldquo;{claim.quote}&rdquo;
          </blockquote>

          <div className="mt-2 flex flex-wrap items-center gap-1.5">
            <VerificationBadge claim={claim} />
            <Badge tone="neutral">{claim.claimType.toLowerCase()}</Badge>
            {claim.corroborationCount > 0 ? (
              <Badge
                tone="good"
                title="Another publisher, on a different domain, said the same thing in this run."
              >
                {claim.corroborationCount} independent {claim.corroborationCount === 1 ? "source agrees" : "sources agree"}
              </Badge>
            ) : null}
            {claim.contradictionCount > 0 ? (
              <Badge
                tone="serious"
                title="Another claim in this run disagrees. Both are shown; neither was chosen."
              >
                {claim.contradictionCount} contradicted
              </Badge>
            ) : null}
            {claim.asOfDate ? <Badge tone="neutral">as of {claim.asOfDate}</Badge> : null}
          </div>

          {showSource ? (
            <div className="mt-2.5 flex flex-wrap items-center gap-x-2 gap-y-1 border-t border-[var(--border-subtle)] pt-2 text-[0.6875rem] text-[var(--text-muted)]">
              <span
                className="inline-block size-2 rounded-[2px]"
                style={{ backgroundColor: SOURCE_TYPE_COLOURS[claim.source.sourceType] }}
                aria-hidden="true"
              />
              <a
                href={claim.source.url}
                target="_blank"
                rel="noopener noreferrer"
                className="font-medium text-[var(--text-link)] hover:underline"
              >
                {claim.source.domain}
              </a>
              <span>· {SOURCE_TYPE_LABELS[claim.source.sourceType]}</span>
              {claim.source.publishedAt ? <span>· {formatDate(claim.source.publishedAt)}</span> : null}
              <span>· credibility {claim.source.credibilityScore}/100</span>
              <span>· found by {claim.source.connectorId}</span>
            </div>
          ) : null}
        </div>
      </div>
    </Panel>
  );
}

/**
 * Whether the quote is really in the page.
 *
 * <p>The tooltip carries the method, not just the verdict, because "verified" is a strong word and
 * a reader is entitled to know it means string matching against stored text rather than a model
 * being asked whether it was telling the truth.
 */
export function VerificationBadge({ claim }: { claim: EvidenceClaim }) {
  if (claim.quoteVerified) {
    return (
      <Badge
        tone="good"
        title={`This quote was located in the page text that was fetched and stored (${Math.round(
          claim.quoteMatchRatio * 100,
        )}% match). Checked by string matching, not by asking a model.`}
        icon={
          <svg viewBox="0 0 12 12" className="size-2.5" aria-hidden="true">
            <path
              d="M2.5 6.4l2.3 2.3 4.7-5"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        }
      >
        Quote verified
      </Badge>
    );
  }
  return (
    <Badge
      tone="warning"
      title="The quote could not be found in the retrieved page text — usually because the page could not be read in full. Treat this claim with caution."
      icon={
        <svg viewBox="0 0 12 12" className="size-2.5" aria-hidden="true">
          <path d="M6 2.2v4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          <circle cx="6" cy="9" r="0.9" fill="currentColor" />
        </svg>
      }
    >
      Unverified quote
    </Badge>
  );
}

export function SourceDetail({ source }: { source: ResearchSource }) {
  const fetchStatus = FETCH_STATUS_TEXT[source.fetchStatus];
  return (
    <div className="mb-4 space-y-3">
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge tone={credibilityTone(source.credibilityScore)}>
          Credibility {source.credibilityScore}/100
        </Badge>
        <Badge tone={fetchStatus.tone} title={fetchStatus.note}>
          {fetchStatus.label}
        </Badge>
        <Badge tone="neutral">{SOURCE_TYPE_LABELS[source.sourceType]}</Badge>
        <Badge tone="neutral">found by {source.connectorId}</Badge>
      </div>

      <a
        href={source.url}
        target="_blank"
        rel="noopener noreferrer"
        className="mono block break-all text-[var(--text-link)] hover:underline"
      >
        {source.url}
      </a>

      {source.snippet ? (
        <Field label="What the search returned" value={source.snippet} />
      ) : null}

      {source.credibilityBreakdown.length > 0 ? (
        <div>
          <p className="eyebrow mb-1.5">How the credibility score was reached</p>
          <ul className="space-y-1">
            {source.credibilityBreakdown.map((component) => (
              <li
                key={component.label}
                className="flex items-baseline justify-between gap-3 border-b border-[var(--border-subtle)] pb-1 text-xs last:border-0"
              >
                <span className="min-w-0 flex-1">
                  <span className="text-[var(--text-secondary)]">{component.label}</span>
                  {component.note ? (
                    <span className="block text-[0.6875rem] text-[var(--text-muted)]">{component.note}</span>
                  ) : null}
                </span>
                <span
                  className="tabular shrink-0 font-semibold"
                  style={{
                    color:
                      component.points > 0
                        ? "var(--text-primary)"
                        : component.points < 0
                          ? "var(--status-critical-ink)"
                          : "var(--text-muted)",
                  }}
                >
                  {component.points > 0 ? "+" : ""}
                  {component.points}
                </span>
              </li>
            ))}
            <li className="flex items-baseline justify-between gap-3 pt-1 text-xs font-semibold">
              <span>Total</span>
              <span className="tabular">{source.credibilityScore}</span>
            </li>
          </ul>
        </div>
      ) : null}
    </div>
  );
}

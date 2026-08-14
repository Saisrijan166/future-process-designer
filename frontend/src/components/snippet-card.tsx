import { Badge, Card } from "@/components/ui";
import { formatDate, hostnameOf, sourceTypeLabel, sourceTypeTone } from "@/lib/format";
import type { KnowledgeSnippet } from "@/lib/types";

/**
 * One curated research source. The link is a real URL to a real document — that is the whole point
 * of the evidence layer, so it is rendered prominently rather than tucked away.
 */
export function SnippetCard({
  snippet,
  footer,
}: {
  snippet: KnowledgeSnippet;
  footer?: React.ReactNode;
}) {
  return (
    <Card as="li" className="flex flex-col p-4 transition-shadow hover:shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <h3 className="text-sm font-semibold text-ink-900">{snippet.title}</h3>
        <Badge tone={sourceTypeTone[snippet.sourceType]}>{sourceTypeLabel[snippet.sourceType]}</Badge>
      </div>

      <p className="mt-2 flex-1 text-[13px] leading-relaxed text-ink-700">{snippet.snippetText}</p>

      {snippet.tags.length > 0 ? (
        <div className="mt-3 flex flex-wrap gap-1">
          {snippet.tags.slice(0, 8).map((tag) => (
            <span key={tag} className="rounded bg-ink-100 px-1.5 py-0.5 text-[11px] text-ink-600">
              {tag}
            </span>
          ))}
        </div>
      ) : null}

      <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 border-t border-ink-100 pt-3 text-xs">
        <a
          href={snippet.sourceUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1 font-medium text-brand-700 hover:underline"
        >
          {hostnameOf(snippet.sourceUrl)} ↗
        </a>
        {snippet.publisher ? <span className="text-ink-500">{snippet.publisher}</span> : null}
        <span className="ml-auto text-ink-400">Retrieved {formatDate(snippet.retrievedAt)}</span>
      </div>

      {footer}
    </Card>
  );
}

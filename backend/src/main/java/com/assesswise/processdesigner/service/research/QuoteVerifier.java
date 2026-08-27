package com.assesswise.processdesigner.service.research;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Checks that a quote actually appears in the page it is attributed to.
 *
 * <p>This is the most important twenty lines of logic in the application. Everything else about the
 * research layer — eleven connectors, content extraction, credibility scoring — is in service of
 * getting real source text in front of a model. What stops the model from then inventing a
 * plausible sentence and attributing it to that source is this class, and nothing else.
 *
 * <p>It is deliberately not clever. Both strings are normalised (case, whitespace, the curly quotes
 * and en-dashes that publishers use and models silently rewrite), and then the quote is looked for
 * in the document. Found means verified. Not found means the claim is kept and marked
 * <em>unverified</em>, which costs it its ability to raise anything's grounding score. No model is
 * asked whether it was telling the truth, because a model that will invent a quote will also
 * confirm one.
 *
 * <p>The fallback path exists because extraction is imperfect, not to be generous: a quote spanning
 * a paragraph boundary, or containing a footnote marker the extractor dropped, will not match
 * exactly while still being an honest quote. So a token-window match is tried, and it has to reach
 * 85% of the quote's own words, in order, before it counts. That threshold is high enough that a
 * fabricated sentence does not reach it — a fabrication shares function words with the source and
 * little else.
 */
@Component
public class QuoteVerifier {

    /** Below this, a token-window match is not treated as the same sentence. */
    private static final double VERIFICATION_THRESHOLD = 0.85;
    /** A "quote" shorter than this is not evidence of anything and is not accepted. */
    private static final int MIN_QUOTE_CHARS = 25;

    /**
     * @param ratio how much of the quote was located, 0..1
     * @param startOffset where it starts in the <em>original</em> document text, so the UI can
     *     highlight it in place; null when only a fuzzy match was found
     * @param method how it matched, recorded for the trace
     */
    public record Verification(boolean verified, double ratio, Integer startOffset, String method) {

        static Verification none(String method) {
            return new Verification(false, 0, null, method);
        }
    }

    public Verification verify(String quote, String documentText) {
        if (quote == null || quote.strip().length() < MIN_QUOTE_CHARS) {
            return Verification.none("quote too short to verify");
        }
        if (documentText == null || documentText.isBlank()) {
            return Verification.none("no source text was retrieved");
        }

        Normalised document = normalise(documentText);
        Normalised needle = normalise(quote);
        if (needle.text().isBlank()) {
            return Verification.none("quote contained no comparable text");
        }

        int index = document.text().indexOf(needle.text());
        if (index >= 0) {
            return new Verification(true, 1.0, document.originalOffsetAt(index), "exact match");
        }

        // Extraction artefacts: a dropped footnote marker, a paragraph join, a stray bullet. Match
        // the quote's words against the best window of the document's words instead.
        TokenMatch match = bestTokenWindow(needle.text(), document.text());
        if (match == null) {
            return Verification.none("not found in the retrieved text");
        }
        boolean verified = match.ratio() >= VERIFICATION_THRESHOLD;
        return new Verification(
                verified,
                round(match.ratio()),
                verified ? document.originalOffsetAt(match.charStart()) : null,
                verified
                        ? "matched %.0f%% of the quote's words in sequence".formatted(match.ratio() * 100)
                        : "only %.0f%% of the quote was found".formatted(match.ratio() * 100));
    }

    /** True when a claim may be used to support a recommendation. */
    public boolean isUsable(Verification verification) {
        return verification != null && verification.verified();
    }

    // -----------------------------------------------------------------------------------------

    /**
     * Normalised text plus a map back to the original offsets, so a match can be highlighted in the
     * text a reader actually sees rather than in this internal form.
     */
    private record Normalised(String text, int[] offsets) {

        Integer originalOffsetAt(int normalisedIndex) {
            if (normalisedIndex < 0 || normalisedIndex >= offsets.length) {
                return null;
            }
            return offsets[normalisedIndex];
        }
    }

    private static Normalised normalise(String source) {
        StringBuilder builder = new StringBuilder(source.length());
        int[] offsets = new int[source.length()];
        boolean lastWasSpace = true;

        for (int index = 0; index < source.length(); index++) {
            char raw = source.charAt(index);
            char mapped = canonical(raw);

            if (Character.isWhitespace(mapped)) {
                if (lastWasSpace) {
                    continue;
                }
                offsets[builder.length()] = index;
                builder.append(' ');
                lastWasSpace = true;
                continue;
            }
            if (mapped == '\0') {
                continue;
            }
            offsets[builder.length()] = index;
            builder.append(Character.toLowerCase(mapped));
            lastWasSpace = false;
        }
        return new Normalised(builder.toString().strip(), offsets);
    }

    /**
     * Folds away the differences that are about typography rather than meaning. Every one of these
     * has been observed breaking an otherwise-honest quote: a model rewrites a curly apostrophe as
     * a straight one, an extractor turns a non-breaking space into a normal one, a publisher uses an
     * en-dash where the model typed a hyphen.
     */
    private static char canonical(char character) {
        return switch (character) {
            // Apostrophes and quotes: models rewrite typographic ones as straight ones constantly.
            case '\u2018', '\u2019', '\u02BC', '\u00B4', '`' -> '\'';
            case '\u201C', '\u201D', '\u00AB', '\u00BB' -> '"';
            // Dashes: publishers use en- and em-dashes where a model types a hyphen.
            case '\u2013', '\u2014', '\u2212', '\u2010' -> '-';
            // Exotic spaces normalise to a plain one; the whitespace branch above then collapses them.
            case '\u00A0', '\u2007', '\u2009', '\u202F', '\u2002', '\u2003' -> ' ';
            // Invisible characters: dropped entirely, since they would break an honest match.
            case '\u2026', '\u200B', '\u200C', '\u200D', '\uFEFF', '\u00AD' -> '\0';
            default -> character;
        };
    }

    private record TokenMatch(double ratio, int charStart) {}

    /**
     * Slides a window the length of the quote across the document and keeps the best overlap.
     *
     * <p>Order is respected rather than treated as a bag of words: the window has to contain the
     * quote's tokens <em>in sequence</em> (allowing gaps), so a paragraph that happens to use the
     * same vocabulary in a different arrangement does not verify a quote it never made.
     */
    private TokenMatch bestTokenWindow(String needle, String haystack) {
        List<Token> needleTokens = tokenise(needle);
        List<Token> haystackTokens = tokenise(haystack);
        if (needleTokens.size() < 4 || haystackTokens.size() < needleTokens.size()) {
            return null;
        }

        int windowSize = Math.min(haystackTokens.size(), (int) Math.ceil(needleTokens.size() * 1.35));
        String anchor = needleTokens.getFirst().value();
        TokenMatch best = null;

        // Only windows that begin with the quote's own first word are worth scoring. Without this
        // anchor the scan is quadratic in document length and a run with fifty claims spends
        // minutes comparing every position of every page against every quote.
        for (int start = 0; start + needleTokens.size() <= haystackTokens.size(); start++) {
            if (!haystackTokens.get(start).value().equals(anchor)) {
                continue;
            }
            int end = Math.min(haystackTokens.size(), start + windowSize);
            int matched = 0;
            int cursor = start;
            for (Token token : needleTokens) {
                while (cursor < end && !haystackTokens.get(cursor).value().equals(token.value())) {
                    cursor++;
                }
                if (cursor >= end) {
                    break;
                }
                matched++;
                cursor++;
            }
            double ratio = (double) matched / needleTokens.size();
            if (best == null || ratio > best.ratio()) {
                best = new TokenMatch(ratio, haystackTokens.get(start).start());
                if (ratio >= 0.999) {
                    break;
                }
            }
        }
        return best;
    }

    private record Token(String value, int start) {}

    private static List<Token> tokenise(String normalisedText) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        int length = normalisedText.length();
        while (index < length) {
            while (index < length && !Character.isLetterOrDigit(normalisedText.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < length && Character.isLetterOrDigit(normalisedText.charAt(index))) {
                index++;
            }
            if (index > start) {
                tokens.add(new Token(normalisedText.substring(start, index).toLowerCase(Locale.ROOT), start));
            }
        }
        return tokens;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}

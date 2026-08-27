package com.assesswise.processdesigner.service.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.ClaimRelationType;
import com.assesswise.processdesigner.domain.ClaimType;
import com.assesswise.processdesigner.domain.EvidenceClaim;
import com.assesswise.processdesigner.domain.ResearchSource;
import com.assesswise.processdesigner.domain.SourceType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-checking claims against each other.
 *
 * <p>Two rules carry the weight, and both are here because breaking either would inflate every
 * confidence score in the application: corroboration counts only across different publishers, and
 * two figures that disagree are recorded as a disagreement rather than averaged into a number
 * neither source supports.
 */
class CorroborationAnalyzerTest {

    private final CorroborationAnalyzer analyzer = new CorroborationAnalyzer();

    @Test
    @DisplayName("two publishers saying the same thing corroborate each other")
    void independentAgreementCounts() {
        EvidenceClaim first = claim("nature.com", "Automated essay scoring agrees with human raters most of the time", null);
        EvidenceClaim second = claim("acm.org", "Automated essay scoring agrees with human raters in most cases", null);

        CorroborationAnalyzer.Analysis analysis = analyzer.analyse(List.of(first, second), credibility(first, second));

        assertThat(analysis.corroborations()).isEqualTo(1);
        assertThat(first.getCorroborationCount()).isEqualTo(1);
        assertThat(analysis.relations()).singleElement()
                .satisfies(relation -> {
                    assertThat(relation.getRelationType()).isEqualTo(ClaimRelationType.CORROBORATES);
                    assertThat(relation.isSameDomain()).isFalse();
                });
    }

    @Test
    @DisplayName("one publisher repeating itself does not")
    void sameDomainRepetitionDoesNotCount() {
        EvidenceClaim first = claim("vendor.example", "Our platform reduces grading effort substantially for exam boards", null);
        EvidenceClaim second = claim("vendor.example", "The platform reduces grading effort substantially for exam boards", null);

        CorroborationAnalyzer.Analysis analysis = analyzer.analyse(List.of(first, second), credibility(first, second));

        // The relation is still recorded — it is a fact about the corpus — but it must not raise
        // anybody's confidence, or four pages from one vendor would look like a consensus.
        assertThat(analysis.relations()).hasSize(1);
        assertThat(analysis.relations().getFirst().isSameDomain()).isTrue();
        assertThat(analysis.corroborations()).isZero();
        assertThat(first.getCorroborationCount()).isZero();
    }

    @Test
    @DisplayName("two figures far apart are a disagreement, not an average")
    void divergentFiguresContradict() {
        EvidenceClaim first = claim("nature.com", "Automated scoring reduced marking turnaround by 30 percent", 30.0);
        EvidenceClaim second = claim("acm.org", "Automated scoring reduced marking turnaround by 70 percent", 70.0);

        CorroborationAnalyzer.Analysis analysis = analyzer.analyse(List.of(first, second), credibility(first, second));

        assertThat(analysis.contradictions()).isEqualTo(1);
        assertThat(first.getContradictionCount()).isEqualTo(1);
        assertThat(analysis.relations().getFirst().getNote()).contains("disagree");
    }

    @Test
    @DisplayName("two figures close together agree")
    void closeFiguresCorroborate() {
        EvidenceClaim first = claim("nature.com", "Automated scoring reduced marking turnaround by 30 percent", 30.0);
        EvidenceClaim second = claim("acm.org", "Automated scoring reduced marking turnaround by 33 percent", 33.0);

        CorroborationAnalyzer.Analysis analysis = analyzer.analyse(List.of(first, second), credibility(first, second));

        assertThat(analysis.contradictions()).isZero();
        assertThat(analysis.corroborations()).isEqualTo(1);
    }

    @Test
    @DisplayName("claims about different things are left alone")
    void unrelatedClaimsAreNotRelated() {
        EvidenceClaim first = claim("nature.com", "Automated essay scoring agrees with human raters most of the time", null);
        EvidenceClaim second = claim("gov.uk", "Candidates must be given fourteen days notice of an examination date", null);

        CorroborationAnalyzer.Analysis analysis = analyzer.analyse(List.of(first, second), credibility(first, second));

        assertThat(analysis.relations()).isEmpty();
    }

    @Test
    @DisplayName("an unverified quote costs a claim most of its confidence")
    void unverifiedClaimsAreDiscounted() {
        EvidenceClaim verified = claim("nature.com", "Automated marking is widely used by examination boards", null);
        EvidenceClaim unverified = claim("acm.org", "A completely unrelated assertion about warehouse logistics", null);
        unverified.setQuoteVerified(false);

        analyzer.analyse(List.of(verified, unverified), credibility(verified, unverified));

        // Both sit on sources of equal credibility, so the gap is entirely the verification.
        assertThat(unverified.getConfidence()).isLessThan(verified.getConfidence() * 0.7);
    }

    private Map<UUID, Integer> credibility(EvidenceClaim... claims) {
        return java.util.Arrays.stream(claims)
                .collect(java.util.stream.Collectors.toMap(
                        claim -> claim.getSource().getId(), claim -> 60, (left, right) -> left));
    }

    private EvidenceClaim claim(String domain, String text, Double numericValue) {
        ResearchSource source = new ResearchSource();
        source.setId(UUID.randomUUID());
        source.setDomain(domain);
        source.setSourceType(SourceType.RESEARCH);
        source.setCredibilityScore(60);

        EvidenceClaim claim = new EvidenceClaim();
        claim.setSource(source);
        claim.setClaimText(text);
        claim.setQuote(text);
        claim.setQuoteVerified(true);
        claim.setClaimType(numericValue == null ? ClaimType.PRACTICE : ClaimType.STATISTIC);
        claim.setNumericValue(numericValue);
        claim.setNumericUnit(numericValue == null ? null : "percent");
        return claim;
    }
}

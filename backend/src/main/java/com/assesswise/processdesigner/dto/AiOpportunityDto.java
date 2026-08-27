package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.AutomationPotential;
import java.util.List;
import java.util.UUID;

/**
 * One AI intervention, with everything needed to judge it rather than just read it.
 *
 * @param groundingScore 0-100, computed from the quote-verified claims cited below. Zero means
 *     nothing checkable supports this — which is allowed, and shown.
 * @param citedClaims the quoted evidence this rests on, in citation order
 * @param review the second model's verdict, or null if the review stage did not run
 * @param impact what it is worth per month, on stated assumptions
 */
public record AiOpportunityDto(
        UUID id,
        UUID activityId,
        String activityName,
        String description,
        String aiCapability,
        AutomationPotential automationPotential,
        String businessBenefit,
        String risk,
        String reasoningNote,
        String rootCause,
        String humanOversight,
        String dataRequirement,
        String successMetric,
        int groundingScore,
        List<KnowledgeSnippetDto> evidence,
        List<EvidenceClaimDto> citedClaims,
        OpportunityScoreDto review,
        ImpactEstimateDto impact) {}

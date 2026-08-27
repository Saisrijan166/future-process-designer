package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.OpportunityVerdict;

/**
 * What the reviewing model thought of one proposal.
 *
 * <p>All five scores are 0-5. {@code riskLevel} and {@code implementationEffort} are the two where
 * higher is worse, which the interface has to render differently — a five for business impact and a
 * five for risk are not the same news.
 */
public record OpportunityScoreDto(
        short feasibility,
        short evidenceStrength,
        short businessImpact,
        short riskLevel,
        short implementationEffort,
        double confidence,
        OpportunityVerdict verdict,
        String critique,
        String reviewerModel,
        int groundedClaimCount) {}

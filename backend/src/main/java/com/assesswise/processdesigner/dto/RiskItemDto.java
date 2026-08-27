package com.assesswise.processdesigner.dto;

import com.assesswise.processdesigner.domain.RiskCategory;
import java.util.List;
import java.util.UUID;

/**
 * One entry in the risk register.
 *
 * @param obligation the specific legal or standards requirement, where the research established one.
 *     Empty rather than invented: an unsupported legal claim in a compliance register is worse than
 *     a gap, so the pipeline strips any obligation that cites no evidence.
 */
public record RiskItemDto(
        UUID id,
        UUID opportunityId,
        String title,
        String description,
        RiskCategory category,
        short likelihood,
        short impact,
        int severityScore,
        String mitigation,
        String ownerRole,
        String obligation,
        List<EvidenceClaimDto> citedClaims) {}

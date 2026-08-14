package com.assesswise.processdesigner.dto;

import java.util.List;

/**
 * One page of the process listing, plus the dataset-wide totals the dashboard leads with.
 *
 * <p>The stats deliberately describe the <em>whole</em> dataset rather than the current page or the
 * active filter: "9 processes, 5 analysed" is a fact about the system, and recomputing it per page
 * would make the headline numbers jump around as the user pages or searches. {@code totalItems},
 * by contrast, does follow the filter — it is what the pager needs.
 */
public record ProcessPageDto(
        List<ProcessSummaryDto> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext,
        Stats stats) {

    /** Counts across every process, independent of the current filter or page. */
    public record Stats(long processes, long analysed, long opportunities, long futureActivities) {}
}

package com.assesswise.processdesigner;

import static org.assertj.core.api.Assertions.assertThat;

import com.assesswise.processdesigner.domain.ProcessOrigin;
import com.assesswise.processdesigner.domain.ProcessStatus;
import com.assesswise.processdesigner.dto.CreateProcessRequest;
import com.assesswise.processdesigner.dto.KnowledgeSnippetDto;
import com.assesswise.processdesigner.dto.ProcessDetailDto;
import com.assesswise.processdesigner.dto.ProcessPageDto;
import com.assesswise.processdesigner.dto.ProcessSummaryDto;
import com.assesswise.processdesigner.dto.UpdateProcessRequest;
import com.assesswise.processdesigner.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ProcessApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static CreateProcessRequest request(String name) {
        return new CreateProcessRequest(
                name,
                "Retail Banking",
                "How a branch opens a current account for a walk-in customer.",
                List.of(
                        new CreateProcessRequest.ActivityInput(
                                "Collect KYC documents", "The officer photocopies identity documents.",
                                List.of("Branch Officer"), List.of("Scanner")),
                        new CreateProcessRequest.ActivityInput(
                                "Verify documents against the register", "Details are checked by hand.",
                                List.of("Branch Officer", "Compliance Officer"), List.of("Core Banking System"))));
    }

    /** Pulls every row across pages, so assertions do not depend on the default page size. */
    private List<ProcessSummaryDto> allProcesses() {
        ProcessPageDto first = restTemplate.getForObject("/api/processes?size=100", ProcessPageDto.class);
        return first.items();
    }

    @Test
    @DisplayName("the six seed processes are present and start un-analysed")
    void seedProcessesArePresent() {
        List<ProcessSummaryDto> processes = allProcesses();

        List<ProcessSummaryDto> seeded = processes.stream()
                .filter(process -> process.origin() == ProcessOrigin.SEED)
                .toList();

        assertThat(seeded).hasSize(6);
        assertThat(seeded).extracting(ProcessSummaryDto::name).containsExactlyInAnyOrder(
                "Online Assessment Creation",
                "Question Bank Management",
                "Candidate Onboarding & Proctoring",
                "Result Evaluation & Grading",
                "Certification Issuance",
                "Learner Support & Doubt Resolution");
        // Nothing about the future state is pre-baked: the demo must generate it live.
        assertThat(seeded).allSatisfy(process -> {
            assertThat(process.status()).isEqualTo(ProcessStatus.CURRENT_ONLY);
            assertThat(process.futureActivityCount()).isZero();
            assertThat(process.opportunityCount()).isZero();
            assertThat(process.activityCount()).isBetween(5L, 6L);
        });
    }

    @Test
    @DisplayName("seed processes carry their roles, systems and recorded pain points")
    void seedProcessesAreFullyPopulated() {
        UUID gradingId = allProcesses().stream()
                .filter(process -> process.name().equals("Result Evaluation & Grading"))
                .findFirst().orElseThrow().id();

        ProcessDetailDto detail = restTemplate.getForObject("/api/processes/" + gradingId, ProcessDetailDto.class);

        assertThat(detail.activities()).hasSize(6);
        assertThat(detail.activities().getFirst().sequenceOrder()).isEqualTo(1);
        assertThat(detail.activities())
                .filteredOn(activity -> activity.name().equals("Grade descriptive answers against the rubric"))
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.roles()).containsExactly("Evaluator");
                    assertThat(activity.systems()).containsExactly("Evaluation Console");
                    assertThat(activity.problems()).isNotEmpty();
                });
        assertThat(detail.problems()).hasSize(3);
        assertThat(detail.futureActivities()).isEmpty();
        assertThat(detail.latestRun()).isNull();
    }

    @Test
    @DisplayName("creates a process with roles and systems, reusing existing lookup rows")
    void createsProcess() {
        String name = "Account Opening " + UUID.randomUUID();

        ResponseEntity<ProcessDetailDto> response =
                restTemplate.postForEntity("/api/processes", request(name), ProcessDetailDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();

        ProcessDetailDto detail = response.getBody();
        assertThat(detail.process().name()).isEqualTo(name);
        assertThat(detail.process().origin()).isEqualTo(ProcessOrigin.USER);
        assertThat(detail.process().status()).isEqualTo(ProcessStatus.CURRENT_ONLY);
        assertThat(detail.activities()).hasSize(2);
        assertThat(detail.activities().get(1).roles()).containsExactly("Branch Officer", "Compliance Officer");

        // Creating a second process reuses the Role rows rather than duplicating them.
        restTemplate.postForEntity("/api/processes", request("Account Opening " + UUID.randomUUID()),
                ProcessDetailDto.class);
        List<?> roles = restTemplate.getForObject("/api/roles", List.class);
        long branchOfficerRows = roles.stream()
                .filter(role -> "Branch Officer".equals(((java.util.Map<?, ?>) role).get("name")))
                .count();
        assertThat(branchOfficerRows).isEqualTo(1);
    }

    @Test
    @DisplayName("trims whitespace and de-duplicates roles case-insensitively")
    void normalisesInput() {
        CreateProcessRequest messy = new CreateProcessRequest(
                "  Messy Input " + UUID.randomUUID() + "  ",
                "  Logistics  ",
                "  A description with padding.  ",
                List.of(new CreateProcessRequest.ActivityInput(
                        "  Do the thing  ", "  With padding.  ",
                        List.of("Clerk", "clerk", " CLERK "), List.of("Tool", ""))));

        ProcessDetailDto detail =
                restTemplate.postForEntity("/api/processes", messy, ProcessDetailDto.class).getBody();

        assertThat(detail.process().name()).doesNotStartWith(" ").doesNotEndWith(" ");
        assertThat(detail.process().industry()).isEqualTo("Logistics");
        assertThat(detail.activities().getFirst().name()).isEqualTo("Do the thing");
        assertThat(detail.activities().getFirst().roles()).hasSize(1);
        assertThat(detail.activities().getFirst().systems()).containsExactly("Tool");
    }

    @Test
    @DisplayName("rejects an invalid create request with per-field messages")
    void rejectsInvalidCreate() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/processes",
                new CreateProcessRequest("", "", "", List.of()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .contains("name is required")
                .contains("industry is required")
                .contains("at least one activity is required");
    }

    @Test
    @DisplayName("updating the activities clears any generated future state")
    void updateResetsAnalysis() {
        String name = "Updatable " + UUID.randomUUID();
        UUID id = restTemplate.postForEntity("/api/processes", request(name), ProcessDetailDto.class)
                .getBody().process().id();

        UpdateProcessRequest update = new UpdateProcessRequest(
                name + " (revised)",
                "Retail Banking",
                "A revised description.",
                List.of(new CreateProcessRequest.ActivityInput(
                        "A single replacement activity", "Replaces both originals.", List.of(), List.of())));

        ResponseEntity<ProcessDetailDto> response = restTemplate.exchange(
                "/api/processes/" + id, HttpMethod.PUT, new HttpEntity<>(update), ProcessDetailDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProcessDetailDto detail = response.getBody();
        assertThat(detail.process().name()).endsWith("(revised)");
        assertThat(detail.process().status()).isEqualTo(ProcessStatus.CURRENT_ONLY);
        assertThat(detail.process().lastAnalyzedAt()).isNull();
        assertThat(detail.activities()).singleElement()
                .satisfies(activity -> assertThat(activity.name()).isEqualTo("A single replacement activity"));
    }

    @Test
    @DisplayName("deletes a process and everything derived from it")
    void deletesProcess() {
        UUID id = restTemplate.postForEntity(
                        "/api/processes", request("Deletable " + UUID.randomUUID()), ProcessDetailDto.class)
                .getBody().process().id();

        ResponseEntity<Void> deleted =
                restTemplate.exchange("/api/processes/" + id, HttpMethod.DELETE, null, Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.getForEntity("/api/processes/" + id, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("every curated snippet has a real source URL and a retrieval date")
    void knowledgeSnippetsAreCited() {
        List<KnowledgeSnippetDto> snippets = restTemplate.exchange(
                "/api/knowledge-snippets", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<KnowledgeSnippetDto>>() {}).getBody();

        assertThat(snippets).hasSize(16);
        assertThat(snippets).allSatisfy(snippet -> {
            assertThat(snippet.sourceUrl()).startsWith("https://");
            assertThat(snippet.snippetText()).isNotBlank();
            assertThat(snippet.sourceType()).isNotNull();
            assertThat(snippet.retrievedAt()).isNotNull();
            assertThat(snippet.tags()).isNotEmpty();
        });
        assertThat(snippets).extracting(snippet -> snippet.sourceType().name()).contains(
                "LAW", "GUIDANCE", "STANDARD", "RESEARCH", "VENDOR", "GENERAL_WEB");
    }

    @Test
    @DisplayName("a comparison for an un-analysed process renders as empty rather than failing")
    void comparisonHandlesUnanalysedProcess() {
        UUID id = restTemplate.postForEntity(
                        "/api/processes", request("Never Analysed " + UUID.randomUUID()), ProcessDetailDto.class)
                .getBody().process().id();

        var comparison = restTemplate.getForObject(
                "/api/processes/" + id + "/comparison",
                com.assesswise.processdesigner.dto.ComparisonDto.class);

        assertThat(comparison.summary().currentActivityCount()).isEqualTo(2);
        assertThat(comparison.summary().futureActivityCount()).isZero();
        assertThat(comparison.summary().problemCount()).isZero();
        assertThat(comparison.future().activities()).isEmpty();
        assertThat(comparison.transition().opportunities()).isEmpty();
        assertThat(comparison.latestRun()).isNull();
    }

    @Test
    @DisplayName("asking for the trace before any analysis returns 404, not an empty shell")
    void traceBeforeAnalysis() {
        UUID id = restTemplate.postForEntity(
                        "/api/processes", request("No Trace Yet " + UUID.randomUUID()), ProcessDetailDto.class)
                .getBody().process().id();

        assertThat(restTemplate.getForEntity(
                        "/api/processes/" + id + "/analysis-runs/latest/trace", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("rejects a malformed UUID with 400 rather than 500")
    void rejectsMalformedId() {
        assertThat(restTemplate.getForEntity("/api/processes/not-a-uuid", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /* ------------------------------------------------------------------ paging */

    @Test
    @DisplayName("pages the listing without dropping or repeating a row")
    void pagesCleanly() {
        ProcessPageDto everything = restTemplate.getForObject("/api/processes?size=100", ProcessPageDto.class);
        long total = everything.totalItems();
        assertThat(total).isGreaterThanOrEqualTo(6);

        List<UUID> walked = new ArrayList<>();
        int page = 0;
        ProcessPageDto current;
        do {
            current = restTemplate.getForObject("/api/processes?size=2&page=" + page, ProcessPageDto.class);
            assertThat(current.page()).isEqualTo(page);
            assertThat(current.size()).isEqualTo(2);
            assertThat(current.items()).hasSizeLessThanOrEqualTo(2);
            current.items().forEach(item -> walked.add(item.id()));
            page++;
        } while (current.hasNext());

        // Every row seen exactly once, and the page count agrees with the row count.
        assertThat(walked).hasSize((int) total).doesNotHaveDuplicates();
        assertThat(page).isEqualTo(current.totalPages());
        assertThat(current.hasNext()).isFalse();
    }

    @Test
    @DisplayName("the status filter applies to the whole dataset, not just the visible page")
    void filtersAcrossTheDataset() {
        ProcessPageDto pending = restTemplate.getForObject(
                "/api/processes?status=CURRENT_ONLY&size=100", ProcessPageDto.class);

        assertThat(pending.items()).isNotEmpty();
        assertThat(pending.items()).allSatisfy(item ->
                assertThat(item.status()).isEqualTo(ProcessStatus.CURRENT_ONLY));
        assertThat(pending.totalItems()).isEqualTo(pending.items().size());
    }

    @Test
    @DisplayName("the headline stats describe the dataset, so filtering does not move them")
    void statsIgnoreTheFilter() {
        ProcessPageDto unfiltered = restTemplate.getForObject("/api/processes", ProcessPageDto.class);
        ProcessPageDto filtered = restTemplate.getForObject(
                "/api/processes?status=ANALYZED&q=zzzz-no-match", ProcessPageDto.class);

        assertThat(filtered.items()).isEmpty();
        assertThat(filtered.totalItems()).isZero();
        // ...but the headline numbers are unchanged.
        assertThat(filtered.stats()).isEqualTo(unfiltered.stats());
        assertThat(filtered.stats().processes()).isGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("search matches name, industry and description")
    void searchesAcrossFields() {
        assertThat(restTemplate.getForObject("/api/processes?q=proctoring", ProcessPageDto.class).items())
                .anySatisfy(item -> assertThat(item.name()).contains("Proctoring"));
        assertThat(restTemplate.getForObject("/api/processes?q=ONLINE EDUCATION", ProcessPageDto.class).items())
                .isNotEmpty();
        assertThat(restTemplate.getForObject("/api/processes?q=certificate", ProcessPageDto.class).items())
                .isNotEmpty();
    }

    @Test
    @DisplayName("a wildcard typed into search is treated as a literal, not as match-everything")
    void escapesWildcards() {
        ProcessPageDto result = restTemplate.getForObject("/api/processes?q=%25", ProcessPageDto.class);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalItems()).isZero();
    }

    @Test
    @DisplayName("sorting by name is honoured despite the grouped projection")
    void sortsByName() {
        List<String> names = restTemplate
                .getForObject("/api/processes?sort=name&size=100", ProcessPageDto.class)
                .items().stream().map(ProcessSummaryDto::name).toList();

        assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
    }

    @Test
    @DisplayName("clamps absurd paging parameters instead of failing")
    void clampsPagingParameters() {
        ProcessPageDto huge = restTemplate.getForObject("/api/processes?size=100000", ProcessPageDto.class);
        assertThat(huge.size()).isEqualTo(100);

        ProcessPageDto negative = restTemplate.getForObject("/api/processes?page=-5&size=0", ProcessPageDto.class);
        assertThat(negative.page()).isZero();
        assertThat(negative.size()).isEqualTo(1);

        ProcessPageDto beyondEnd = restTemplate.getForObject("/api/processes?page=9999", ProcessPageDto.class);
        assertThat(beyondEnd.items()).isEmpty();
        assertThat(beyondEnd.hasNext()).isFalse();
    }

    @Test
    @DisplayName("rejects an unknown status value with 400")
    void rejectsUnknownStatus() {
        assertThat(restTemplate.getForEntity("/api/processes?status=BANANA", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

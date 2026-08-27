package com.assesswise.processdesigner.service.pipeline;

import com.assesswise.processdesigner.domain.Activity;
import com.assesswise.processdesigner.domain.Role;
import com.assesswise.processdesigner.domain.StageStatus;
import com.assesswise.processdesigner.domain.SystemTool;
import com.assesswise.processdesigner.service.progress.ProgressEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Reads the process and says what it is working with — before any model is involved.
 *
 * <p>No AI here, deliberately. Its job is to establish the facts every later stage depends on and to
 * surface the gaps in them, because a model handed a process with no activity descriptions and no
 * recorded systems will confidently invent both. Naming the gap up front means the analysis can be
 * honest about its own inputs: an analysis of a three-line process description is a different thing
 * from an analysis of a fully documented one, and the scorecard should be able to tell.
 *
 * <p>It is also the stage that makes the "surprise process" test pass visibly. A process created
 * thirty seconds ago in an industry nobody anticipated arrives here and is described in terms of
 * what it actually contains, with no branch anywhere on which process it is.
 */
@Component
@Order(10)
public class IntakeStage implements PipelineStage {

    @Override
    public String id() {
        return "intake";
    }

    @Override
    public String title() {
        return "Read the current process";
    }

    @Override
    public StageResult execute(PipelineContext context) {
        List<Activity> activities = context.activities();
        Set<String> roles = new LinkedHashSet<>();
        Set<String> systems = new LinkedHashSet<>();
        int withoutDescription = 0;
        int withoutRoles = 0;
        int withoutSystems = 0;

        for (Activity activity : activities) {
            activity.getRoles().stream().map(Role::getName).forEach(roles::add);
            activity.getSystems().stream().map(SystemTool::getName).forEach(systems::add);
            if (activity.getDescription() == null || activity.getDescription().isBlank()) {
                withoutDescription++;
            }
            if (activity.getRoles().isEmpty()) {
                withoutRoles++;
            }
            if (activity.getSystems().isEmpty()) {
                withoutSystems++;
            }
        }

        List<String> notes = new ArrayList<>();
        if (activities.isEmpty()) {
            notes.add("This process has no activities recorded, so every stage after this one is "
                    + "working from the description alone.");
        }
        if (withoutDescription > 0) {
            notes.add(("%d of %d activities have no description; the analysis of those steps rests on "
                            + "their name alone.").formatted(withoutDescription, activities.size()));
        }
        if (withoutRoles > 0) {
            notes.add("%d activities have no roles recorded, so who does the work today is unknown for them."
                    .formatted(withoutRoles));
        }
        if (withoutSystems > 0) {
            notes.add(("%d activities have no systems recorded, so integration effort is being estimated "
                            + "rather than derived.").formatted(withoutSystems));
        }
        if (context.knownProblems().isEmpty()) {
            notes.add("No problems were recorded by the team, so the diagnosis is entirely derived rather "
                    + "than partly reported.");
        }
        context.addWarnings(notes);

        String summary = "%d activities, %d roles, %d systems, %d problems already reported".formatted(
                activities.size(), roles.size(), systems.size(), context.knownProblems().size());

        context.sink().emit(ProgressEvent.Type.STAGE_FINISHED, id(), title(), summary,
                Map.of("activities", activities.size(),
                        "roles", List.copyOf(roles),
                        "systems", List.copyOf(systems),
                        "gaps", notes));

        // A process with nothing but a name is analysable, but the result should say so rather than
        // looking as solid as one built on documented activities.
        StageStatus status = activities.isEmpty() ? StageStatus.DEGRADED : StageStatus.SUCCEEDED;
        return StageResult.succeeded(summary).withStatus(status).withNotes(notes);
    }
}

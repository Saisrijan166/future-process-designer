package com.assesswise.processdesigner.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A business process in its CURRENT state.
 *
 * <p>Named {@code BusinessProcess} rather than {@code Process} to avoid colliding with
 * {@link java.lang.Process}; the table is still {@code process}.
 */
@Entity
@Table(name = "process")
@Getter
@Setter
@NoArgsConstructor
public class BusinessProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "industry", nullable = false, length = 120)
    private String industry;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessStatus status = ProcessStatus.CURRENT_ONLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private ProcessOrigin origin = ProcessOrigin.USER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_analyzed_at")
    private Instant lastAnalyzedAt;

    @OneToMany(mappedBy = "process", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<Activity> activities = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addActivity(Activity activity) {
        activity.setProcess(this);
        activities.add(activity);
    }
}

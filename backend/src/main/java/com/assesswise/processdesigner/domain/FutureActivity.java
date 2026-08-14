package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A step in the FUTURE state of a process, stored as a row with an explicit human/AI
 * responsibility split — this is the "not a paragraph of prose" requirement.
 */
@Entity
@Table(name = "future_activity")
@Getter
@Setter
@NoArgsConstructor
public class FutureActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_id", nullable = false)
    private BusinessProcess process;

    @Column(name = "name", nullable = false, length = 250)
    private String name;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "human_responsibility", columnDefinition = "text")
    private String humanResponsibility;

    @Column(name = "ai_responsibility", columnDefinition = "text")
    private String aiResponsibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsibility_type", nullable = false, length = 20)
    private ResponsibilityType responsibilityType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

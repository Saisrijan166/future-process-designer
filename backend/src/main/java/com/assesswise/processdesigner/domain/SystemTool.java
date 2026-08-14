package com.assesswise.processdesigner.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Shared lookup of the systems/tools that support activities, e.g. "Proctoring Engine". */
@Entity
@Table(name = "system_tool")
@Getter
@Setter
@NoArgsConstructor
public class SystemTool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150, unique = true)
    private String name;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SystemTool(String name, String type) {
        this.name = name;
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

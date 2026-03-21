package com.invokingmachines.multistorage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meta_column", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"table_id", "name"}),
        @UniqueConstraint(columnNames = {"table_id", "alias"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetaColumnEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private MetaTableEntity table;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String alias;

    @Column(name = "data_type")
    private String dataType;

    @Column(nullable = false)
    @Builder.Default
    private Boolean readable = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean searchable = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean editable = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (alias == null) alias = name;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

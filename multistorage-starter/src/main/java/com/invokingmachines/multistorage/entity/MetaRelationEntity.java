package com.invokingmachines.multistorage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meta_relation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetaRelationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_table_id", nullable = false)
    private MetaTableEntity fromTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_table_id", nullable = false)
    private MetaTableEntity toTable;

    @Column(name = "from_column", nullable = false)
    private String fromColumn;

    @Column(name = "to_column", nullable = false)
    private String toColumn;

    @Column(name = "is_one_to_many", nullable = false)
    private Boolean oneToMany;

    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

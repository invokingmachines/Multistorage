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
    @JoinColumn(name = "many_table_id", nullable = false)
    private MetaTableEntity manyTable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "one_table_id", nullable = false)
    private MetaTableEntity oneTable;

    @Column(name = "many_column", nullable = false)
    private String manyColumn;

    @Column(name = "one_column", nullable = false)
    private String oneColumn;

    @Column(nullable = false)
    private String name;

    @Column(name = "inverse_name")
    private String inverseName;

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

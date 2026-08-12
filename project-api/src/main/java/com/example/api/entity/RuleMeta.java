package com.example.api.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_meta")
public class RuleMeta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(length = 20)
    private String lastBuildStatus;

    @Column(columnDefinition = "TEXT")
    private String lastBuildMessage;

    private LocalDateTime lastBuildAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RuleMeta() {
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (version == null) {
            version = 1;
        }
        if (status == null) {
            status = "ENABLED";
        }
        if (type == null) {
            type = "DROOLS";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getLastBuildStatus() { return lastBuildStatus; }
    public void setLastBuildStatus(String lastBuildStatus) { this.lastBuildStatus = lastBuildStatus; }
    public String getLastBuildMessage() { return lastBuildMessage; }
    public void setLastBuildMessage(String lastBuildMessage) { this.lastBuildMessage = lastBuildMessage; }
    public LocalDateTime getLastBuildAt() { return lastBuildAt; }
    public void setLastBuildAt(LocalDateTime lastBuildAt) { this.lastBuildAt = lastBuildAt; }
}

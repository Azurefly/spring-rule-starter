
package com.example.api.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_meta")
public class RuleMeta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name;

    private String type; // DROOLS | CUSTOM
    
    @Column(columnDefinition = "TEXT")
    private String content;

    private String status;
    private Integer version = 1;
    private String lastBuildStatus;
    private String lastBuildMessage;
    private java.time.LocalDateTime lastBuildAt;

    private String createdBy;
    private LocalDateTime createdAt;

    public RuleMeta() { this.createdAt = LocalDateTime.now(); }

    // getters & setters
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

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getLastBuildStatus() { return lastBuildStatus; }
    public void setLastBuildStatus(String lastBuildStatus) { this.lastBuildStatus = lastBuildStatus; }
    public String getLastBuildMessage() { return lastBuildMessage; }
    public void setLastBuildMessage(String lastBuildMessage) { this.lastBuildMessage = lastBuildMessage; }
    public java.time.LocalDateTime getLastBuildAt() { return lastBuildAt; }
    public void setLastBuildAt(java.time.LocalDateTime lastBuildAt) { this.lastBuildAt = lastBuildAt; }
}

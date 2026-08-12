package com.example.api.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "rule_build_history")
public class RuleBuildHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String ruleName;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String builtBy;

    @Column(nullable = false)
    private LocalDateTime builtAt;

    public RuleBuildHistory() {
    }

    @PrePersist
    public void prePersist() {
        if (builtAt == null) {
            builtAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getBuiltBy() { return builtBy; }
    public void setBuiltBy(String builtBy) { this.builtBy = builtBy; }
    public LocalDateTime getBuiltAt() { return builtAt; }
    public void setBuiltAt(LocalDateTime builtAt) { this.builtAt = builtAt; }
}

package com.seveninterprise.clusterforge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Modelo para alertas de clusters
 * 
 * Representa alertas gerados pelo sistema de monitoramento:
 * - Alertas de recursos (CPU, memória, disco)
 * - Alertas de aplicação (tempo de resposta, disponibilidade)
 * - Alertas de sistema (falhas, recuperações)
 * - Alertas customizados
 */
@Entity
@Table(name = "cluster_alerts")
public class ClusterAlert {
    
    public enum AlertSeverity {
        CRITICAL,   // Requer ação imediata
        WARNING,    // Pode se tornar crítico
        INFO,       // Informativo
        RECOVERY    // Notificação de recuperação
    }
    
    public enum AlertStatus {
        ACTIVE,     // Alerta ativo
        RESOLVED,   // Alerta resolvido
        SUPPRESSED, // Alerta suprimido
        EXPIRED     // Alerta expirado
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;
    
    @Column(name = "alert_type", nullable = false)
    private String alertType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.ACTIVE;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by")
    private String resolvedBy;
    
    @Column(name = "resolution_message", columnDefinition = "TEXT")
    private String resolutionMessage;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "acknowledged_by")
    private String acknowledgedBy;
    
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;
    
    @Column(name = "escalation_level")
    private Integer escalationLevel = 0;
    
    @Column(name = "notification_sent")
    private boolean notificationSent = false;
    
    @Column(name = "notification_channels")
    private String notificationChannels; // JSON array de canais
    
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON com dados adicionais
    
    @Column(name = "rule_name")
    private String ruleName;
    
    @Column(name = "metric_value")
    private String metricValue;
    
    @Column(name = "threshold_value")
    private String thresholdValue;
    
    @Column(name = "consecutive_violations")
    private Integer consecutiveViolations = 1;
    
    @Column(name = "cooldown_until")
    private LocalDateTime cooldownUntil;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Cluster getCluster() {
        return cluster;
    }
    
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }
    
    public String getAlertType() {
        return alertType;
    }
    
    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }
    
    public AlertSeverity getSeverity() {
        return severity;
    }
    
    public void setSeverity(AlertSeverity severity) {
        this.severity = severity;
    }
    
    public AlertStatus getStatus() {
        return status;
    }
    
    public void setStatus(AlertStatus status) {
        this.status = status;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
    
    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
    
    public String getResolvedBy() {
        return resolvedBy;
    }
    
    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }
    
    public String getResolutionMessage() {
        return resolutionMessage;
    }
    
    public void setResolutionMessage(String resolutionMessage) {
        this.resolutionMessage = resolutionMessage;
    }
    
    public LocalDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }
    
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }
    
    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }
    
    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }
    
    public LocalDateTime getEscalatedAt() {
        return escalatedAt;
    }
    
    public void setEscalatedAt(LocalDateTime escalatedAt) {
        this.escalatedAt = escalatedAt;
    }
    
    public Integer getEscalationLevel() {
        return escalationLevel;
    }
    
    public void setEscalationLevel(Integer escalationLevel) {
        this.escalationLevel = escalationLevel;
    }
    
    public boolean isNotificationSent() {
        return notificationSent;
    }
    
    public void setNotificationSent(boolean notificationSent) {
        this.notificationSent = notificationSent;
    }
    
    public String getNotificationChannels() {
        return notificationChannels;
    }
    
    public void setNotificationChannels(String notificationChannels) {
        this.notificationChannels = notificationChannels;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getMetricValue() {
        return metricValue;
    }
    
    public void setMetricValue(String metricValue) {
        this.metricValue = metricValue;
    }
    
    public String getThresholdValue() {
        return thresholdValue;
    }
    
    public void setThresholdValue(String thresholdValue) {
        this.thresholdValue = thresholdValue;
    }
    
    public Integer getConsecutiveViolations() {
        return consecutiveViolations;
    }
    
    public void setConsecutiveViolations(Integer consecutiveViolations) {
        this.consecutiveViolations = consecutiveViolations;
    }
    
    public LocalDateTime getCooldownUntil() {
        return cooldownUntil;
    }
    
    public void setCooldownUntil(LocalDateTime cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }
}

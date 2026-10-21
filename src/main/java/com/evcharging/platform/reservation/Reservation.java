package com.evcharging.platform.reservation;

import com.evcharging.platform.connector.Connector;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connector_id", nullable = false)
    private Connector connector;

    @Column(name = "user_ref", nullable = false, length = 100)
    private String userRef;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "quoted_price_per_kwh", precision = 8, scale = 4)
    private BigDecimal quotedPricePerKwh;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Reservation() {
        // Required by JPA
    }

    public Reservation(Connector connector, String userRef,
                       OffsetDateTime startTime, OffsetDateTime endTime) {
        this.connector = connector;
        this.userRef = userRef;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public Connector getConnector() { return connector; }
    public String getUserRef() { return userRef; }
    public OffsetDateTime getStartTime() { return startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public ReservationStatus getStatus() { return status; }
    public BigDecimal getQuotedPricePerKwh() { return quotedPricePerKwh; }
    public void setQuotedPricePerKwh(BigDecimal price) { this.quotedPricePerKwh = price; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }
}

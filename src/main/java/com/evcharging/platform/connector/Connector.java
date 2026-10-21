package com.evcharging.platform.connector;

import com.evcharging.platform.station.Station;
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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "connectors",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_connector_number_per_station",
        columnNames = {"station_id", "connector_number"}
    )
)
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "connector_number", nullable = false, length = 50)
    private String connectorNumber;

    @Column(name = "power_kw", nullable = false, precision = 7, scale = 2)
    private BigDecimal powerKw;

    @Column(name = "base_price_per_kwh", nullable = false, precision = 8, scale = 4)
    private BigDecimal basePricePerKwh = new BigDecimal("0.3000");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConnectorStatus status = ConnectorStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Connector() {
        // Required by JPA
    }

    public Connector(Station station, String connectorNumber, BigDecimal powerKw) {
        this.station = station;
        this.connectorNumber = connectorNumber;
        this.powerKw = powerKw;
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
    public Station getStation() { return station; }
    public String getConnectorNumber() { return connectorNumber; }
    public BigDecimal getPowerKw() { return powerKw; }
    public BigDecimal getBasePricePerKwh() { return basePricePerKwh; }
    public ConnectorStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public boolean isActive() { return status == ConnectorStatus.ACTIVE; }
    public void activate() { this.status = ConnectorStatus.ACTIVE; }
    public void deactivate() { this.status = ConnectorStatus.INACTIVE; }
}

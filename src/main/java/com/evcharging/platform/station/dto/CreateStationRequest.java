package com.evcharging.platform.station.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStationRequest(

    @NotBlank(message = "Station name is required")
    @Size(max = 150, message = "Station name must not exceed 150 characters")
    String name,

    @NotBlank(message = "Station address is required")
    @Size(max = 300, message = "Station address must not exceed 300 characters")
    String address,

    @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
    Double latitude,

    @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
    Double longitude

) {}

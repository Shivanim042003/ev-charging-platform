package com.evcharging.platform.connector.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateConnectorRequest(

    @NotBlank(message = "Connector number is required")
    @Size(max = 50, message = "Connector number must not exceed 50 characters")
    String connectorNumber,

    @NotNull(message = "Power (kW) is required")
    @DecimalMin(value = "0.01", message = "Power must be greater than zero")
    @Digits(integer = 5, fraction = 2, message = "Power must have at most 5 integer digits and 2 decimals")
    BigDecimal powerKw

) {}

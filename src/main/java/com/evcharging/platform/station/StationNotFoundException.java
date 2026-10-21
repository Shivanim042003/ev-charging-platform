package com.evcharging.platform.station;

import java.util.UUID;

public class StationNotFoundException extends RuntimeException {

    public StationNotFoundException(UUID id) {
        super("Station not found: " + id);
    }
}

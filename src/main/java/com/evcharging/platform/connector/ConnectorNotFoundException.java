package com.evcharging.platform.connector;

import java.util.UUID;

public class ConnectorNotFoundException extends RuntimeException {

    public ConnectorNotFoundException(UUID id) {
        super("Connector not found: " + id);
    }
}

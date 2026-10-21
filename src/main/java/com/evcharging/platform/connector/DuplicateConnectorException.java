package com.evcharging.platform.connector;

public class DuplicateConnectorException extends RuntimeException {

    public DuplicateConnectorException(String connectorNumber) {
        super("Connector number already exists at this station: " + connectorNumber);
    }
}

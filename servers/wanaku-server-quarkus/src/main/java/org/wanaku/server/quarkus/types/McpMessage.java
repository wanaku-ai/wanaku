package org.wanaku.server.quarkus.types;

import java.util.UUID;

import org.jboss.logging.Logger;

public class McpMessage {
    private static final Logger LOG = Logger.getLogger(McpMessage.class);
    public String event;
    public String payload;


    public static McpMessage newConnectionMessage(String host, int port) {
        McpMessage message = new McpMessage();

        message.event = "endpoint";
        message.payload = endpoint(host, port);
        return message;
    }

    public static String endpoint(String host, int port) {
        String uuid = UUID.randomUUID().toString();
        LOG.infof("Created new session %s", uuid);

        return endpoint(host, port, uuid);
    }

    public static String endpoint(String host, int port, String uuid) {
        return String.format("%s/message?sessionId=%s", baseAddress(host, port), uuid);
    }

    private static String baseAddress(String host, int port) {
        return String.format("http://%s:%d", host, port);
    }
}

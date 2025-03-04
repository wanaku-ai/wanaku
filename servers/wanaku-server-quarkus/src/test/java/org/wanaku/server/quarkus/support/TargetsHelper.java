package org.wanaku.server.quarkus.support;

import java.util.Map;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;

public class TargetsHelper {
    public static final String RESOURCE_TARGETS_INDEX = "target/test-classes/resource-targets.json";
    public static final String TOOLS_TARGETS_INDEX = "target/test-classes/tools-targets.json";

    public static Map<String, Service> getResourceTargets() {
        Service service = createService("localhost:9002", "test", "Test configuration");

        return Map.of("file", service);
    }

    public static Map<String, Service> getToolsTargets() {
        Service service1 = createService("localhost:9000", "test", "Test configuration 1");
        Service service2 = createService("localhost:9001", "test2", "Test configuration 2");

        return Map.of("http", service1, "camel-route", service2);
    }

    private static Service createService(String target, String name, String description) {
        Service service = new Service();
        service.setTarget(target);
        Configurations configurations = new Configurations();
        Configuration configuration = new Configuration();
        configuration.setDescription(description);
        configurations.getConfigurations().put(name, configuration);
        service.setConfigurations(configurations);
        return service;
    }
}

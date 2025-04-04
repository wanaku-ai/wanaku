package ai.wanaku.cli.main.support;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;

public class PrettyPrinter {

    private PrettyPrinter() {}

    public static void printParseable(final ToolReference toolReference) {
        System.out.printf("%-15s => %-15s => %-30s    %n",
                toolReference.getName(), toolReference.getType(), toolReference.getUri());
    }

    /**
     * Prints a list of references
     * @param list the list of references
     */
    public static void printTools(final List<ToolReference> list) {
        System.out.printf("%-15s    %-15s    %-30s    %n",
                "Name", "Type", "URI");

        for (ToolReference toolReference : list) {
            printParseable(toolReference);
        }
    }

    public static void printParseable(final ResourceReference resourceReference) {
        System.out.printf("%-20s => %-15s => %-30s    %s%n",
                resourceReference.getName(), resourceReference.getType(), resourceReference.getLocation(),
                resourceReference.getDescription());
    }

    /**
     * Prints a list of resources
     * @param list the list of resources
     */
    public static void printResources(final List<ResourceReference> list) {
        System.out.printf("%-20s    %-15s    %-30s    %s%n",
                "Name", "Type", "Location", "Description");

        for (ResourceReference packageInfo : list) {
            printParseable(packageInfo);
        }
    }


    public static void printParseableTarget(String name, Service service) {
        Map<String, Configuration> configurations = service.getConfigurations().getConfigurations();
        String strings = configurations.keySet().stream().sorted().collect(Collectors.joining(", "));
        System.out.printf("%-20s => %-30s => %-30s%n", name, service.getTarget(), strings);
    }

    /**
     * Prints a map of entries
     * @param map the map of entries
     */
    public static void printTargets(final Map<String, Service> map) {
        System.out.printf("%-20s    %-30s    %-30s%n",
                "Service", "Target", "Configurations");

        for (var entry : map.entrySet()) {
            printParseableTarget(entry.getKey(), entry.getValue());
        }
    }


    /**
     * Prints a map of entries
     * @param states the map of states
     */
    public static void printStates(final Map<String, List<State>> states) {
        System.out.printf("%-20s    %-10s    %-60s%n",
                "Service", "Healthy", "Message");

        for (var entry : states.entrySet()) {
            for (var state : entry.getValue()) {
                printParseableState(entry.getKey(), state.healthy(), state.message());
            }
        }
    }

    private static void printParseableState(String service, boolean healthy, String message) {
        System.out.printf("%-15s => %-15s => %-30s    %n",
                service, Boolean.valueOf(healthy), message);
    }


}

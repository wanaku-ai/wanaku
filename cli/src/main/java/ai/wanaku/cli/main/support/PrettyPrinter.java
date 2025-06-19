package ai.wanaku.cli.main.support;

import ai.wanaku.api.types.ForwardReference;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PrettyPrinter {
    private static final DateTimeFormatter DATA_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private PrettyPrinter() {}

    public static void printTool(final ToolReference toolReference) {
        System.out.printf("%-15s => %-15s => %-30s    %n",
                toolReference.getName(), toolReference.getType(), toolReference.getUri());
    }

    /**
     * Prints a list of references
     * @param list the list of references
     */
    public static void printTools(final List<ToolReference> list) {
        System.out.printf("%-15s    %-15s    %-30s    %n",
                "NAME", "TYPE", "URI");

        list.forEach(PrettyPrinter::printTool);
    }

    public static void printResourceRef(final ResourceReference resourceReference) {
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
                "NAME", "TYPE", "LOCATION", "DESCRIPTION");

        list.forEach(PrettyPrinter::printResourceRef);
    }

    public static void printTarget(ServiceTarget service) {
        System.out.printf("%-37s => %-20s => %-30s%n", service.getId(), service.getService(), service.getHost(), service.getPort());
    }

    /**
     * Prints a map of entries
     * @param map the map of entries
     */
    public static void printTargets(final List<ServiceTarget> map) {
        System.out.printf("%-37s    %-20s    %-30s%n",
                "ID", "SERVICE", "HOST");

        map.forEach(PrettyPrinter::printTarget);
    }


    /**
     * Prints a map of entries
     * @param states the map of states
     */
    public static void printStates(final Map<String, List<ActivityRecord>> states) {
        for (var entry : states.entrySet()) {
            printState(entry.getKey(), entry.getValue());
        }
    }

    private static void printState(String serviceName, List<ActivityRecord> activityRecord) {
        System.out.printf("%-37s    %-15s    %-15s    %-10s%n",
                "ID", "SERVICE", "ACTIVE", "LAST SEEN");
        activityRecord.forEach(r -> printState(serviceName, r));
    }

    private static void printState(String serviceName, ActivityRecord activityRecord) {
        String lastSeen;

        if (activityRecord == null) {
            System.out.printf("|- %s: no available registered services%n", serviceName);
            return;
        }

        if (activityRecord.getLastSeen() != null) {
            lastSeen = DATA_TIME_FORMATTER.format(activityRecord.getLastSeen());
        } else {
            lastSeen = "unknown";
        }

        System.out.printf("|- %-37s    %-15s    %-15s    %-10s%n",
                activityRecord.getId(), serviceName, activityRecord.isActive(), lastSeen);

        final List<ServiceState> states = activityRecord.getStates();
        for (var state : states) {
            System.out.printf("%-5s|- %s at %s: '%s' %n",
                    " ", state.isHealthy() ? "Healthy" : "Unhealthy",
                    DATA_TIME_FORMATTER.format(state.getTimestamp()), state.getReason());
        }
        System.out.println();
    }


    private static void printForwardReference(ForwardReference reference) {
        System.out.printf("%-20s    %-60s%n", reference.getName(), reference.getAddress());
    }

    /**
     * Prints a list of resources
     * @param list the list of resources
     */
    public static void printForwards(final List<ForwardReference> list) {
        System.out.printf("%-20s    %-60s%n", "SERVICE", "ADDRESS");

        list.forEach(PrettyPrinter::printForwardReference);
    }
}

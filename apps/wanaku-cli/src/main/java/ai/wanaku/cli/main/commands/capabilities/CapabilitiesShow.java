package ai.wanaku.cli.main.commands.capabilities;

import java.io.IOException;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CapabilitiesHelper;
import ai.wanaku.cli.main.support.CommandHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.CapabilitiesService;

import static ai.wanaku.cli.main.support.CapabilitiesHelper.API_TIMEOUT;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.fetchAndMergeCapabilities;
import static ai.wanaku.cli.main.support.CapabilitiesHelper.printCapability;
import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

@Command(name = "show", description = "Show detailed information about a specific capability")
public class CapabilitiesShow extends BaseCommand {

    private static final String DEFAULT_HOST = "http://localhost:8080";

    private static final String CAPABILITY_CHOICE_FORMAT = "%-20s  %-20s  %-5d  %-10s  %-45s";

    @Option(
            names = {"--host"},
            description = "The API host URL (default: " + DEFAULT_HOST + ")",
            defaultValue = DEFAULT_HOST)
    private String host;

    @Parameters(description = "The service name to show details for (e.g., http, sqs, file)", arity = "1..1")
    private String service;

    private CapabilitiesService capabilitiesService;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        capabilitiesService = initService(CapabilitiesService.class, host);

        List<CapabilitiesHelper.PrintableCapability> capabilities =
                fetchAndMergeCapabilities(capabilitiesService).await().atMost(API_TIMEOUT).stream()
                        .filter(capability -> capability.service().equals(service))
                        .toList();

        return switch (capabilities.size()) {
            case 0 -> handleNoCapabilities(printer);
            case 1 -> handleSingleCapability(printer, capabilities.getFirst());
            default -> handleMultipleCapabilities(terminal, printer, capabilities);
        };
    }

    private Integer handleNoCapabilities(WanakuPrinter printer) throws IOException {
        printer.printWarningMessage("No capabilities found for service: " + service);
        return EXIT_ERROR;
    }

    private Integer handleSingleCapability(WanakuPrinter printer, CapabilitiesHelper.PrintableCapability capability)
            throws Exception {
        printCapabilityDetails(printer, capability);
        return EXIT_OK;
    }

    private Integer handleMultipleCapabilities(
            Terminal terminal, WanakuPrinter printer, List<CapabilitiesHelper.PrintableCapability> capabilities)
            throws Exception {

        printer.printWarningMessage("Multiple capabilities found for the " + service + " service. Please choose one.");

        List<String> choices =
                capabilities.stream().map(this::formatCapabilityChoice).toList();

        try {
            int selectedIndex = CommandHelper.selectFromList(terminal, "Select a capability instance:", choices);
            printCapabilityDetails(printer, capabilities.get(selectedIndex));
            return EXIT_OK;
        } catch (Exception e) {
            printer.printErrorMessage("Error during capability selection: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private String formatCapabilityChoice(CapabilitiesHelper.PrintableCapability capability) {
        return String.format(
                CAPABILITY_CHOICE_FORMAT,
                capability.serviceType(),
                capability.host(),
                capability.port(),
                capability.status(),
                capability.lastSeen());
    }

    private void printCapabilityDetails(WanakuPrinter printer, CapabilitiesHelper.PrintableCapability capability)
            throws Exception {
        printer.printInfoMessage("Capability Details:");
        printCapability(capability, printer);

        printer.printInfoMessage("\nConfigurations:");
        printer.printTable(capability.configurations(), "name", "description");
    }
}

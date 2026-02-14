package ai.wanaku.cli.main.commands.capabilities;

import static picocli.CommandLine.Command;
import static picocli.CommandLine.Option;

import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.jline.terminal.Terminal;

/**
 * CLI command for watching real-time capability events via Server-Sent Events (SSE).
 *
 * <p>This command connects to the capabilities notifications endpoint and displays
 * events as they occur, including capability registrations, deregistrations, updates,
 * and health pings. The command runs continuously until interrupted with Ctrl+C.</p>
 *
 * <p>Usage examples:</p>
 * <pre>
 * # Watch capability events from localhost
 * wanaku capabilities watch
 *
 * # Watch events from a remote host
 * wanaku capabilities watch --host http://remote:8080
 * </pre>
 *
 * @see CapabilitiesStatus
 * @see CapabilitiesList
 */
@Command(name = "watch", description = "Watch real-time capability registration and status events")
public class CapabilitiesWatch extends BaseCommand {

    private static final String NOTIFICATIONS_PATH = "/api/v1/capabilities/notifications";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Option(
            names = {"--host"},
            description = "The API host URL (default: http://localhost:8080)",
            defaultValue = "http://localhost:8080")
    private String host;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        String url = host + NOTIFICATIONS_PATH;
        printer.printInfoMessage("Connecting to " + url + " ...");
        printer.printInfoMessage("Press Ctrl+C to stop watching.\n");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                printer.printErrorMessage("Failed to connect: HTTP " + response.statusCode());
                return EXIT_ERROR;
            }

            printer.printSuccessMessage("Connected. Watching for capability events...\n");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                String eventType = null;
                String eventId = null;
                StringBuilder dataBuilder = new StringBuilder();

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("event:")) {
                        eventType = line.substring("event:".length()).trim();
                    } else if (line.startsWith("id:")) {
                        eventId = line.substring("id:".length()).trim();
                    } else if (line.startsWith("data:")) {
                        if (!dataBuilder.isEmpty()) {
                            dataBuilder.append("\n");
                        }
                        dataBuilder.append(line.substring("data:".length()).trim());
                    } else if (line.isEmpty()) {
                        // Empty line marks end of event
                        if (eventType != null || !dataBuilder.isEmpty()) {
                            printEvent(printer, eventType, eventId, dataBuilder.toString());
                            eventType = null;
                            eventId = null;
                            dataBuilder.setLength(0);
                        }
                    }
                }
            }
        } catch (java.net.ConnectException e) {
            printer.printErrorMessage("Unable to connect to " + url + ". Is the server running?");
            return EXIT_ERROR;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        printer.printInfoMessage("\nDisconnected.");
        return EXIT_OK;
    }

    private void printEvent(WanakuPrinter printer, String eventType, String eventId, String data) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String type = eventType != null ? eventType : "unknown";

        switch (type.toUpperCase()) {
            case "REGISTER" ->
                printer.printSuccessMessage(
                        String.format("[%s] REGISTER  id=%s  %s", timestamp, truncate(eventId), formatData(data)));
            case "DEREGISTER" ->
                printer.printErrorMessage(
                        String.format("[%s] DEREGISTER  id=%s  %s", timestamp, truncate(eventId), formatData(data)));
            case "UPDATE" ->
                printer.printWarningMessage(
                        String.format("[%s] UPDATE  id=%s  %s", timestamp, truncate(eventId), formatData(data)));
            case "PING" -> printer.printInfoMessage(String.format("[%s] PING  id=%s", timestamp, truncate(eventId)));
            default ->
                printer.printInfoMessage(
                        String.format("[%s] %s  id=%s  %s", timestamp, type, truncate(eventId), formatData(data)));
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "-";
        }
        if (value.length() <= 16) {
            return value;
        }
        return value.substring(0, 13) + "...";
    }

    private static String formatData(String data) {
        if (data == null || data.isBlank()) {
            return "";
        }
        // Show a compact single-line summary of the JSON data
        return data.replace("\n", " ");
    }
}

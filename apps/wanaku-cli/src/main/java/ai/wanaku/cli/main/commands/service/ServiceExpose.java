package ai.wanaku.cli.main.commands.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.NamespaceOptions;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "expose", description = "Generate Wanaku rules from Camel routes")
public class ServiceExpose extends BaseCommand {

    private static final Pattern ROUTE_BLOCK_PATTERN = Pattern.compile("^(\\s*)-\\s+route:");
    private static final Pattern ID_LINE_PATTERN = Pattern.compile("^(\\s*)id:\\s*[\"']?([^\"'\\s]+)[\"']?");

    @CommandLine.Option(
            names = {"--path"},
            description = "Path to the service catalog directory",
            defaultValue = ".",
            arity = "0..1")
    private String path;

    @CommandLine.ArgGroup(exclusive = true, multiplicity = "0..1")
    NamespaceOptions namespaceOptions;

    @CommandLine.Option(
            names = {"--type"},
            description = "Type of MCP endpoint to expose: tool or resource",
            defaultValue = "tool",
            arity = "0..1")
    private String type;

    @CommandLine.Option(
            names = {"--uri"},
            description = "URI pattern for resource endpoints (default: wanaku://{route-id})",
            arity = "0..1")
    private String uri;

    @CommandLine.Option(
            names = {"--mime-type"},
            description = "MIME type for resource endpoints",
            defaultValue = "application/octet-stream",
            arity = "0..1")
    private String mimeType;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        if (type == null || type.isBlank()) {
            type = "tool";
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "application/octet-stream";
        }
        if (!"tool".equals(type) && !"resource".equals(type)) {
            printer.printErrorMessage(String.format("Invalid type '%s': must be 'tool' or 'resource'", type));
            return EXIT_ERROR;
        }

        File baseDir = new File(path);
        File indexFile = new File(baseDir, "index.properties");

        if (!indexFile.exists()) {
            printer.printErrorMessage(String.format("index.properties not found in '%s'", path));
            return EXIT_ERROR;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(indexFile)) {
            props.load(fis);
        }

        String servicesStr = props.getProperty("catalog.services");
        if (servicesStr == null || servicesStr.isBlank()) {
            printer.printErrorMessage("No services defined in index.properties");
            return EXIT_ERROR;
        }

        String[] systems = servicesStr.split(",");
        int totalRoutes = 0;

        for (String system : systems) {
            String systemName = system.trim();
            if (systemName.isEmpty()) {
                continue;
            }

            String routesPath = props.getProperty("catalog.routes." + systemName);
            if (routesPath == null) {
                printer.printErrorMessage(
                        String.format("No routes path defined for system '%s' in index.properties", systemName));
                return EXIT_ERROR;
            }

            File routeFile = new File(baseDir, routesPath);
            if (!routeFile.exists()) {
                printer.printErrorMessage(String.format("Route file not found: %s", routeFile.getPath()));
                return EXIT_ERROR;
            }

            // Extract route IDs from the Camel YAML
            List<String> routeIds = extractRouteIds(routeFile);
            if (routeIds.isEmpty()) {
                printer.printInfoMessage(
                        String.format("No route IDs found in %s, skipping rules generation", routeFile.getPath()));
                continue;
            }

            // Generate rules file
            String rulesPath = props.getProperty("catalog.rules." + systemName);
            if (rulesPath == null) {
                rulesPath = systemName + "/" + systemName + ".wanaku-rules.yaml";
            }

            File rulesFile = new File(baseDir, rulesPath);
            if ("resource".equals(type)) {
                generateResourceRulesFile(rulesFile, systemName, routeIds);
            } else {
                generateToolRulesFile(rulesFile, systemName, routeIds);
            }

            totalRoutes += routeIds.size();
            printer.printInfoMessage(
                    String.format("Generated rules for system '%s': %d route(s) exposed", systemName, routeIds.size()));
        }

        printer.printSuccessMessage(
                String.format("Expose complete: %d route(s) across %d system(s)", totalRoutes, systems.length));
        return EXIT_OK;
    }

    /**
     * Extract route IDs from a Camel YAML file by matching only the {@code id:} that is
     * a direct child of a {@code - route:} block, ignoring {@code id:} fields nested in steps.
     */
    private List<String> extractRouteIds(File routeFile) throws IOException {
        List<String> routeIds = new ArrayList<>();
        int expectedIdIndent = -1;

        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(routeFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher routeMatcher = ROUTE_BLOCK_PATTERN.matcher(line);
                if (routeMatcher.find()) {
                    expectedIdIndent = routeMatcher.group(1).length() + 4;
                    continue;
                }

                if (expectedIdIndent < 0) {
                    continue;
                }

                Matcher idMatcher = ID_LINE_PATTERN.matcher(line);
                if (idMatcher.find() && idMatcher.group(1).length() == expectedIdIndent) {
                    routeIds.add(idMatcher.group(2));
                    expectedIdIndent = -1;
                }
            }
        }
        return routeIds;
    }

    /**
     * Generate a Wanaku rules YAML file exposing each route as an MCP tool.
     */
    private void generateToolRulesFile(File rulesFile, String systemName, List<String> routeIds) throws IOException {
        File parentDir = rulesFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + parentDir);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(rulesFile, StandardCharsets.UTF_8))) {
            pw.println("# Auto-generated Wanaku rules for " + systemName);
            pw.println("# Generated by 'wanaku service expose'");
            pw.println("mcp:");
            pw.println("  tools:");

            for (String routeId : routeIds) {
                pw.println("    - " + routeId + ":");
                pw.println("        route:");
                pw.println("          id: \"" + routeId + "\"");
                pw.println("        description: \"Invoke route " + routeId + " in " + systemName + "\"");
                if (namespaceOptions != null) {
                    String ns = namespaceOptions.getNamespaceValue();
                    if (ns != null && !ns.isBlank()) {
                        pw.println("        namespace: \"" + ns + "\"");
                    }
                }
                pw.println("        properties:");
                pw.println("          - name: wanaku_body");
                pw.println("            type: string");
                pw.println("            description: The input data for route " + routeId);
                pw.println("            required: true");
            }
        }
    }

    /**
     * Generate a Wanaku rules YAML file exposing each route as an MCP resource.
     */
    private void generateResourceRulesFile(File rulesFile, String systemName, List<String> routeIds)
            throws IOException {
        File parentDir = rulesFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + parentDir);
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(rulesFile, StandardCharsets.UTF_8))) {
            pw.println("# Auto-generated Wanaku rules for " + systemName);
            pw.println("# Generated by 'wanaku service expose'");
            pw.println("mcp:");
            pw.println("  resources:");

            for (String routeId : routeIds) {
                String resourceUri = uri != null ? uri : "wanaku://" + routeId;
                pw.println("    - " + routeId + ":");
                pw.println("        route:");
                pw.println("          id: \"" + routeId + "\"");
                pw.println("        description: \"Resource from route " + routeId + " in " + systemName + "\"");
                pw.println("        uri: \"" + resourceUri + "\"");
                pw.println("        mimeType: \"" + mimeType + "\"");
                if (namespaceOptions != null) {
                    String ns = namespaceOptions.getNamespaceValue();
                    if (ns != null && !ns.isBlank()) {
                        pw.println("        namespace: \"" + ns + "\"");
                    }
                }
            }
        }
    }
}

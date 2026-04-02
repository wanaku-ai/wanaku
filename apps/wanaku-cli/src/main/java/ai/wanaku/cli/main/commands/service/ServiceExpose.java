package ai.wanaku.cli.main.commands.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "expose", description = "Generate Wanaku rules from Camel routes")
public class ServiceExpose extends BaseCommand {

    private static final Pattern ROUTE_ID_PATTERN = Pattern.compile("^\\s*id:\\s*[\"']?([^\"'\\s]+)[\"']?");

    @CommandLine.Option(
            names = {"--path"},
            description = "Path to the service catalog directory",
            defaultValue = ".",
            arity = "0..1")
    private String path;

    @CommandLine.Option(
            names = {"--namespace"},
            description = "Namespace for generated rules",
            arity = "0..1")
    private String namespace;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        File baseDir = new File(path);
        File indexFile = new File(baseDir, "index.properties");

        if (!indexFile.exists()) {
            printer.printErrorMessage(String.format("index.properties not found in '%s'%n", path));
            return EXIT_ERROR;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(indexFile)) {
            props.load(fis);
        }

        String servicesStr = props.getProperty("catalog.services");
        if (servicesStr == null || servicesStr.isBlank()) {
            printer.printErrorMessage("No services defined in index.properties%n");
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
                        String.format("No routes path defined for system '%s' in index.properties%n", systemName));
                return EXIT_ERROR;
            }

            File routeFile = new File(baseDir, routesPath);
            if (!routeFile.exists()) {
                printer.printErrorMessage(String.format("Route file not found: %s%n", routeFile.getPath()));
                return EXIT_ERROR;
            }

            // Extract route IDs from the Camel YAML
            List<String> routeIds = extractRouteIds(routeFile);
            if (routeIds.isEmpty()) {
                printer.printInfoMessage(
                        String.format("No route IDs found in %s, skipping rules generation%n", routeFile.getPath()));
                continue;
            }

            // Generate rules file
            String rulesPath = props.getProperty("catalog.rules." + systemName);
            if (rulesPath == null) {
                rulesPath = systemName + "/" + systemName + ".wanaku-rules.yaml";
            }

            File rulesFile = new File(baseDir, rulesPath);
            generateRulesFile(rulesFile, systemName, routeIds);

            totalRoutes += routeIds.size();
            printer.printInfoMessage(String.format(
                    "Generated rules for system '%s': %d route(s) exposed%n", systemName, routeIds.size()));
        }

        printer.printSuccessMessage(
                String.format("Expose complete: %d route(s) across %d system(s)%n", totalRoutes, systems.length));
        return EXIT_OK;
    }

    /**
     * Extract route IDs from a Camel YAML file by looking for "id:" fields.
     */
    private List<String> extractRouteIds(File routeFile) throws IOException {
        List<String> routeIds = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(routeFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ROUTE_ID_PATTERN.matcher(line);
                if (matcher.find()) {
                    routeIds.add(matcher.group(1));
                }
            }
        }
        return routeIds;
    }

    /**
     * Generate a Wanaku rules YAML file exposing each route as an MCP tool.
     */
    private void generateRulesFile(File rulesFile, String systemName, List<String> routeIds) throws IOException {
        File parentDir = rulesFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(rulesFile))) {
            pw.println("# Auto-generated Wanaku rules for " + systemName);
            pw.println("# Generated by 'wanaku service expose'");
            pw.println("mcp:");
            pw.println("  tools:");

            for (String routeId : routeIds) {
                pw.println("    - " + routeId + ":");
                pw.println("        route:");
                pw.println("          id: \"" + routeId + "\"");
                pw.println("        description: \"Invoke route " + routeId + " in " + systemName + "\"");
                if (namespace != null && !namespace.isBlank()) {
                    pw.println("        namespace: \"" + namespace + "\"");
                }
            }
        }
    }
}

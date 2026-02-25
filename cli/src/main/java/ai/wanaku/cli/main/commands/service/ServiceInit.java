package ai.wanaku.cli.main.commands.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "init", description = "Initialize a new service catalog structure")
public class ServiceInit extends BaseCommand {

    @CommandLine.Option(
            names = {"--name"},
            description = "The service catalog name",
            required = true)
    private String name;

    @CommandLine.Option(
            names = {"--services"},
            description = "Comma-separated list of system identifiers",
            required = true)
    private String services;

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws Exception {
        String[] systems = services.split(",");

        // Create root directory
        File rootDir = new File(name);
        if (rootDir.exists()) {
            printer.printErrorMessage(String.format("Directory '%s' already exists%n", name));
            return EXIT_ERROR;
        }

        if (!rootDir.mkdirs()) {
            printer.printErrorMessage(String.format("Failed to create directory '%s'%n", name));
            return EXIT_ERROR;
        }

        // Create index.properties
        try {
            createIndexProperties(rootDir, systems);
        } catch (IOException e) {
            printer.printErrorMessage(String.format("Failed to create index.properties: %s%n", e.getMessage()));
            return EXIT_ERROR;
        }

        // Create system directories and skeleton files
        for (String system : systems) {
            String systemName = system.trim();
            if (systemName.isEmpty()) {
                continue;
            }

            try {
                createSystemFiles(rootDir, systemName);
            } catch (IOException e) {
                printer.printErrorMessage(
                        String.format("Failed to create files for system '%s': %s%n", systemName, e.getMessage()));
                return EXIT_ERROR;
            }
        }

        printer.printSuccessMessage(String.format("Service catalog '%s' initialized successfully%n", name));
        printer.printInfoMessage(String.format("Next steps:%n"));
        printer.printInfoMessage(
                String.format("  1. Edit the Camel route files (*.camel.yaml) with your integration routes%n"));
        printer.printInfoMessage(String.format("  2. Run 'wanaku service expose --path=%s' to generate rules%n", name));
        printer.printInfoMessage(
                String.format("  3. Run 'wanaku service deploy --path=%s' to deploy the service%n", name));

        return EXIT_OK;
    }

    private void createIndexProperties(File rootDir, String[] systems) throws IOException {
        Properties props = new Properties();
        props.setProperty("catalog.name", name);
        props.setProperty("catalog.description", "Service catalog for " + name);
        props.setProperty("catalog.services", services);

        for (String system : systems) {
            String systemName = system.trim();
            if (systemName.isEmpty()) {
                continue;
            }
            props.setProperty("catalog.routes." + systemName, systemName + "/" + systemName + ".camel.yaml");
            props.setProperty("catalog.rules." + systemName, systemName + "/" + systemName + ".wanaku-rules.yaml");
            props.setProperty(
                    "catalog.dependencies." + systemName, systemName + "/" + systemName + ".dependencies.txt");
        }

        File indexFile = new File(rootDir, "index.properties");
        try (FileWriter writer = new FileWriter(indexFile)) {
            props.store(writer, "Service Catalog Index - " + name);
        }
    }

    private void createSystemFiles(File rootDir, String systemName) throws IOException {
        File systemDir = new File(rootDir, systemName);
        if (!systemDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + systemDir.getPath());
        }

        // Create skeleton Camel route YAML
        File routeFile = new File(systemDir, systemName + ".camel.yaml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(routeFile))) {
            pw.println("# Camel routes for " + systemName);
            pw.println("- route:");
            pw.println("    id: " + systemName + "-route");
            pw.println("    from:");
            pw.println("      uri: \"direct:" + systemName + "\"");
            pw.println("      steps:");
            pw.println("        - log:");
            pw.println("            message: \"Processing " + systemName + " request\"");
        }

        // Create skeleton Wanaku rules YAML
        File rulesFile = new File(systemDir, systemName + ".wanaku-rules.yaml");
        try (PrintWriter pw = new PrintWriter(new FileWriter(rulesFile))) {
            pw.println("# Wanaku rules for " + systemName);
            pw.println("# This file will be auto-generated by 'wanaku service expose'");
            pw.println("mcp:");
            pw.println("  tools: []");
        }

        // Create empty dependencies file
        File depsFile = new File(systemDir, systemName + ".dependencies.txt");
        try (PrintWriter pw = new PrintWriter(new FileWriter(depsFile))) {
            pw.println("# Maven dependencies for " + systemName);
            pw.println("# One dependency per line in format: groupId:artifactId:version");
        }
    }
}

package ai.wanaku.cli.main.commands.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Base64;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jline.terminal.Terminal;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import picocli.CommandLine;

@CommandLine.Command(name = "package", description = "Package a service catalog into a Base64-encoded ZIP")
public class ServicePackage extends BaseCommand {

    @CommandLine.Option(
            names = {"--path"},
            description = "Path to the service catalog directory",
            defaultValue = ".",
            arity = "0..1")
    private String path;

    @CommandLine.Option(
            names = {"--output", "-o"},
            description = "Output file path (defaults to <catalog-name>.b64)",
            arity = "0..1")
    private String output;

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

        String catalogName = props.getProperty("catalog.name");
        if (catalogName == null || catalogName.isBlank()) {
            printer.printErrorMessage("Missing required property 'catalog.name' in index.properties%n");
            return EXIT_ERROR;
        }

        String servicesStr = props.getProperty("catalog.services");
        if (servicesStr == null || servicesStr.isBlank()) {
            printer.printErrorMessage("Missing required property 'catalog.services' in index.properties%n");
            return EXIT_ERROR;
        }

        String[] systems = servicesStr.split(",");
        for (String system : systems) {
            String systemName = system.trim();
            if (systemName.isEmpty()) {
                continue;
            }

            String routesPath = props.getProperty("catalog.routes." + systemName);
            if (routesPath == null) {
                printer.printErrorMessage(
                        String.format("Missing 'catalog.routes.%s' in index.properties%n", systemName));
                return EXIT_ERROR;
            }
            if (!new File(baseDir, routesPath).exists()) {
                printer.printErrorMessage(String.format("Route file not found: %s%n", routesPath));
                return EXIT_ERROR;
            }

            String rulesPath = props.getProperty("catalog.rules." + systemName);
            if (rulesPath == null) {
                printer.printErrorMessage(
                        String.format("Missing 'catalog.rules.%s' in index.properties%n", systemName));
                return EXIT_ERROR;
            }
            if (!new File(baseDir, rulesPath).exists()) {
                printer.printErrorMessage(String.format("Rules file not found: %s%n", rulesPath));
                return EXIT_ERROR;
            }
        }

        printer.printInfoMessage(String.format("Packaging service catalog '%s'...%n", catalogName));

        byte[] zipBytes;
        try {
            zipBytes = createZipArchive(baseDir);
        } catch (IOException e) {
            printer.printErrorMessage(String.format("Failed to create ZIP archive: %s%n", e.getMessage()));
            return EXIT_ERROR;
        }

        String base64Data = Base64.getEncoder().encodeToString(zipBytes);

        String outputPath = output;
        if (outputPath == null || outputPath.isBlank()) {
            outputPath = catalogName + ".b64";
        }

        File outputFile = new File(outputPath);
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(base64Data);
        }

        printer.printSuccessMessage(String.format(
                "Service catalog '%s' packaged to '%s' (%d bytes zipped)%n",
                catalogName, outputFile.getPath(), zipBytes.length));
        return EXIT_OK;
    }

    private byte[] createZipArchive(File baseDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addDirectoryToZip(zos, baseDir, "");
        }
        return baos.toByteArray();
    }

    private void addDirectoryToZip(ZipOutputStream zos, File dir, String prefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            String entryName = prefix.isEmpty() ? file.getName() : prefix + "/" + file.getName();

            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, entryName);
            } else {
                zos.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }
}

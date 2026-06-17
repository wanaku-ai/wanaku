package ai.wanaku.backend.api.v1.servicecatalog;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import ai.wanaku.core.services.api.ServiceCatalogIndex;

final class ForageDependencyAppender {

    private ForageDependencyAppender() {}

    static void append(
            Map<String, byte[]> entries, ServiceCatalogIndex index, Collection<String> forageGavs, String serviceSystem)
            throws IOException {
        if (forageGavs == null || forageGavs.isEmpty()) {
            return;
        }

        for (String system : index.getServiceNames()) {
            String depsPath = index.getDependenciesFile(system);

            if (depsPath != null && entries.containsKey(depsPath)) {
                appendToExisting(entries, depsPath, forageGavs, system, serviceSystem);
            } else {
                createNew(entries, forageGavs, system, serviceSystem);
            }
        }
    }

    private static void appendToExisting(
            Map<String, byte[]> entries,
            String depsPath,
            Collection<String> forageGavs,
            String system,
            String serviceSystem) {
        String existing = new String(entries.get(depsPath));
        Set<String> merged = new LinkedHashSet<>(parseDependenciesFile(existing));

        for (String gav : forageGavs) {
            merged.add(gav);
        }

        entries.put(depsPath, formatDependencies(merged));

        if (serviceSystem != null && !serviceSystem.isBlank()) {
            String remappedPath = depsPath.replace(system + "/", serviceSystem + "/");
            if (!remappedPath.equals(depsPath) && !entries.containsKey(remappedPath)) {
                entries.put(remappedPath, entries.get(depsPath));
            }
        }
    }

    private static void createNew(
            Map<String, byte[]> entries, Collection<String> forageGavs, String system, String serviceSystem)
            throws IOException {
        String effectiveSystem = (serviceSystem != null && !serviceSystem.isBlank()) ? serviceSystem : system;
        String newDepsPath = effectiveSystem + "/" + effectiveSystem + ".dependencies.txt";

        entries.put(newDepsPath, formatDependencies(forageGavs));

        byte[] indexBytes = entries.get("index.properties");
        if (indexBytes != null) {
            Properties indexProps = new Properties();
            indexProps.load(new StringReader(new String(indexBytes)));
            indexProps.setProperty("catalog.dependencies." + effectiveSystem, newDepsPath);
            StringWriter indexWriter = new StringWriter();
            indexProps.store(indexWriter, null);
            entries.put("index.properties", indexWriter.toString().getBytes());
        }
    }

    static Set<String> parseDependenciesFile(String content) {
        Set<String> gavs = new LinkedHashSet<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                gavs.add(trimmed);
            }
        }
        return gavs;
    }

    private static byte[] formatDependencies(Collection<String> gavs) {
        StringBuilder sb = new StringBuilder();
        for (String gav : gavs) {
            sb.append(gav).append('\n');
        }
        return sb.toString().getBytes();
    }
}

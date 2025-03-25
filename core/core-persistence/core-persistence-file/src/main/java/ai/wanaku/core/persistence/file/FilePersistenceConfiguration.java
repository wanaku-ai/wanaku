package ai.wanaku.core.persistence.file;

import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

public class FilePersistenceConfiguration {

    @ConfigProperty(name = "wanaku.persistence.file.base-folder", defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @ConfigProperty(name = "wanaku.persistence.file.resource-reference", defaultValue = "resources.json")
    String resourceReferenceFileName;

    @ConfigProperty(name = "wanaku.persistence.file.tool-reference", defaultValue = "tools.json")
    String toolReferenceFileName;

    @ConfigProperty(name = "wanaku.persistence.file.services", defaultValue = "targets.json")
    String servicesFileName;

    @LookupUnlessProperty(name = "wanaku.persistence", stringValue = "mongodb", lookupIfMissing = true)
    @Produces
    ResourceReferenceRepository resourceReferenceRepository() {
        return new FileResourceReferenceRepository(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), resourceReferenceFileName));
    }

    @LookupUnlessProperty(name = "wanaku.persistence", stringValue = "mongodb", lookupIfMissing = true)
    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new FileToolReferenceRepository(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), toolReferenceFileName));
    }

    @LookupUnlessProperty(name = "wanaku.service.persistence", stringValue = "valkey", lookupIfMissing = true)
    @Produces
    ServiceRegistry serviceRegistry() {
        return new FileServiceRegistry(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), servicesFileName),
                Path.of(
                        baseFolder.replace("${user.home}", System.getProperty("user.home"))));
    }
}

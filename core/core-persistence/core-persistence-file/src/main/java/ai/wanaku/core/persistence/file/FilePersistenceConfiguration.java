package ai.wanaku.core.persistence.file;

import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ServiceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

@ApplicationScoped
public class FilePersistenceConfiguration {

    @ConfigProperty(name = "wanaku.persistence.file.base-folder", defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @ConfigProperty(name = "wanaku.persistence.file.resource-reference", defaultValue = "resources.json")
    String resourceReferenceFileName;

    @ConfigProperty(name = "wanaku.persistence.file.tool-reference", defaultValue = "tools.json")
    String toolReferenceFileName;

    @ConfigProperty(name = "wanaku.persistence.file.services", defaultValue = "targets.json")
    String servicesFileName;

    @DefaultBean
    @Produces
    ResourceReferenceRepository resourceReferenceRepository() {
        return new FileResourceReferenceRepository(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), resourceReferenceFileName));
    }

    @DefaultBean
    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new FileToolReferenceRepository(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), toolReferenceFileName));
    }

    @DefaultBean
    @Produces
    ServiceRepository serviceRepository() {
        return new FileServiceRepository(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), servicesFileName));
    }
}

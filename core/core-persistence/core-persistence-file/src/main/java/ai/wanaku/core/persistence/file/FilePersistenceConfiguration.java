package ai.wanaku.core.persistence.file;

import jakarta.enterprise.inject.Produces;

import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import io.quarkus.arc.lookup.LookupUnlessProperty;
import java.nio.file.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Deprecated
public class FilePersistenceConfiguration {

    @ConfigProperty(name = "wanaku.persistence.file.base-folder", defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @ConfigProperty(name = "wanaku.persistence.file.services", defaultValue = "targets.json")
    String servicesFileName;

    @LookupUnlessProperty(name = "wanaku.service.persistence", stringValue = "valkey", lookupIfMissing = true)
    @Produces
    ServiceRegistry serviceRegistry() {
        return new FileServiceRegistry(new WanakuMarshallerService(), Path.of(
                baseFolder.replace("${user.home}", System.getProperty("user.home")), servicesFileName),
                Path.of(
                        baseFolder.replace("${user.home}", System.getProperty("user.home"))));
    }
}

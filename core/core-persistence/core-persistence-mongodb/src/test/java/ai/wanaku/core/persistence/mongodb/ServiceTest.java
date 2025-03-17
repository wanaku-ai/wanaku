package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.persistence.api.ServiceRepository;
import com.mongodb.client.MongoClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;

@QuarkusTestResource(MongoDBResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@QuarkusTest
public class ServiceTest {

    @Inject
    ServiceRepository serviceRepository;

    @Inject
    MongoClient mongoClient;

    @Order(1)
    @Test
    public void persist() {
        for (int i = 1; i < 4; i++) {
            Service service = new Service();
            service.setName("test" + i);
            service.setTarget("target" + i);
            service.setServiceType("resource-provider");
            Configurations configurations = new Configurations();
            configurations.setConfigurations(new HashMap<>());
            Configuration configuration = new Configuration();
            configuration.setValue("value" + i);
            configuration.setDescription("description" + i);
            configurations.getConfigurations().put("key" + i, configuration);
            service.setConfigurations(configurations);

            serviceRepository.persist(service);
        }

        Assertions.assertTrue(mongoClient.getDatabase("wanaku").getCollection("service").countDocuments() == 3);
    }

    @Order(2)
    @Test
    public void list() {
        List<Service> services = serviceRepository.listAll();

        Assertions.assertTrue(services.size() == 3);
    }
}

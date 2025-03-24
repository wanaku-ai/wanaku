package ai.wanaku.core.persistence.mongodb;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ServiceRepository;
import ai.wanaku.core.persistence.types.ServiceTargetEntity;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.ArrayList;
import java.util.List;

public class MongoDBServiceRepository extends AbstractMongoDBRepository<ServiceTarget, ServiceTargetEntity, String>
        implements ServiceRepository {

    public MongoDBServiceRepository(MongoClient mongoClient, WanakuMarshallerService wanakuMarshallerService) {
        super(mongoClient, wanakuMarshallerService);
    }

    @Override
    String databaseName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.database.name", String.class);
    }

    @Override
    String collectionName() {
        return ConfigProvider.getConfig().getValue("wanaku.mongodb.service.collection.name", String.class);
    }

    @Override
    Class<ServiceTargetEntity> getEntityClass() {
        return ServiceTargetEntity.class;
    }

    @Override
    public List<ServiceTarget> listByServiceType(ServiceType serviceType) {
        List<ServiceTarget> services = new ArrayList<>();

        for (Document type : mongoCollection().find(Filters.eq("serviceType", serviceType))) {
            ServiceTargetEntity serviceEntity = wanakuMarshallerService.unmarshalOne(type.toJson(), ServiceTargetEntity.class);
            services.add(convertToModel(serviceEntity));
        }

        return services;
    }

    @Override
    public ServiceTarget findByIdAndServiceType(String id, ServiceType serviceType) {
        Bson filter = Filters.and(Filters.eq("_id", id), Filters.eq("serviceType", serviceType));
        Document document = mongoCollection().find(filter).first();

        if (document == null) {
            throw new ServiceNotFoundException("Service with ID " + id + " and type " + serviceType.asValue() + " not found");
        }

        return convertToModel(wanakuMarshallerService.unmarshalOne(document.toJson(), ServiceTargetEntity.class));
    }

    @Override
    public ServiceTarget update(ServiceTarget model) {
        ServiceTargetEntity entity = convertToEntity(model);

        String marshalled = wanakuMarshallerService.marshal(entity);

        Document document = Document.parse(marshalled);
        document.append("_id", entity.getId());

        mongoCollection().replaceOne(Filters.eq("_id", entity.getId()), document);

        return convertToModel(entity);
    }
}

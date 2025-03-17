package ai.wanaku.core.persistence.file;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.ServiceRepository;
import ai.wanaku.core.persistence.types.ServiceEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FileServiceRepository extends AbstractFileRepository<Service, ServiceEntity, String> implements ServiceRepository {

    public FileServiceRepository(WanakuMarshallerService wanakuMarshallerService, Path file) {
        super(wanakuMarshallerService, file);
    }

    @Override
    Class<ServiceEntity> getEntityClass() {
        return ServiceEntity.class;
    }

    @Override
    public List<Service> listByServiceType(ServiceType serviceType) {
        try {
            String data = Files.readString(file);

            List<ServiceEntity> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());

            return convertToModels(entities.stream()
                    .filter(e -> serviceType.equals(e.getServiceType()))
                    .collect(Collectors.toList()));
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + file.toString(), e);
        }
    }

    @Override
    public Service findByIdAndServiceType(String id, ServiceType serviceType) {
        try {
            String data = Files.readString(file);

            List<ServiceEntity> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());

            return convertToModel(entities.stream()
                    .filter(e -> e.getServiceType().equals(serviceType) && e.getId().equals(id))
                    .findFirst().orElseThrow(() -> new ServiceNotFoundException("Service with id: " + id + " and serviceType " + serviceType + " not found")));
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + file.toString(), e);
        }
    }

    @Override
    public Service update(Service model) {
        try {
            ServiceEntity entity = convertToEntity(model);
            String data = Files.readString(file);

            List<ServiceEntity> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());

            List<ServiceEntity> s = entities.stream()
                    .filter(e -> !e.getId().equals(entity.getId()))
                    .collect(Collectors.toList());

            s.add(entity);
            String marshalledEntity = wanakuMarshallerService.marshal(s);

            Files.deleteIfExists(file);
            Files.write(file, marshalledEntity.getBytes(), StandardOpenOption.CREATE);

            return convertToModel(entity);
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + file.toString(), e);
        }
    }
}

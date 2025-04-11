package ai.wanaku.core.persistence.file;

import ai.wanaku.api.exceptions.ServiceNotFoundException;
import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.api.types.management.State;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.mcp.providers.ServiceTarget;
import ai.wanaku.core.mcp.providers.ServiceType;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.types.ServiceTargetEntity;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import ai.wanaku.core.util.RuntimeInfo;
import org.jboss.logging.Logger;

public class FileServiceRegistry extends AbstractFileRepository<ServiceTarget, ServiceTargetEntity, String> implements ServiceRegistry {
    private static final Logger LOG = Logger.getLogger(FileServiceRegistry.class);

    private final Path folder;
    private final ReentrantLock lock = new ReentrantLock();

    public FileServiceRegistry(WanakuMarshallerService wanakuMarshallerService, Path file, Path folder) {
        super(wanakuMarshallerService, file);
        this.folder = folder;
    }

    private String tryRead(Path file) throws IOException {
        return Files.readString(file);
    }

    private void tryWriteWindows(Path file, byte[] content) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file.toFile())) {
            boolean written = false;
            int retries = 10;
            do {
                if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                    Files.write(file, content, StandardOpenOption.TRUNCATE_EXISTING);
                    written = true;
                } else {
                    LOG.warnf("Overlapping file lock: %s", file.getFileName());
                    try {
                        Thread.sleep(Duration.ofMillis(100).toMillis());
                    } catch (InterruptedException ex) {
                        throw new WanakuException(ex);
                    }
                    retries--;
                }
            } while (!written && retries > 0);
        } catch (InterruptedException ex) {
            throw new WanakuException(ex);
        }

    }

    private void tryWrite(Path file, byte[] content) throws IOException {
        if (RuntimeInfo.isWindows()) {
            tryWriteWindows(file, content);
        } else {
            tryWriteNormal(file, content);
        }
    }

    private void tryWriteNormal(Path file, byte[] content) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file.toFile())) {
            boolean written = false;
            int retries = 10;
            do {
                try (FileLock lock = stream.getChannel().lock()) {
                    Files.write(file, content, StandardOpenOption.TRUNCATE_EXISTING);
                    written = true;
                } catch (OverlappingFileLockException e) {
                    LOG.warnf("Overlapping file lock: %s", file.getFileName());
                    try {
                        Thread.sleep(Duration.ofMillis(100).toMillis());
                    } catch (InterruptedException ex) {
                        throw new WanakuException(ex);
                    }
                    retries--;
                }
            } while (!written && retries > 0);
        }
    }

    @Override
    public void register(ServiceTarget serviceTarget, Map<String, String> configurations) {
        try {
            String data = tryRead(file);

            List<ServiceTargetEntity> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());

            ServiceTargetEntity entity = convertToEntity(serviceTarget);

            ServiceTargetEntity entityFromFile = entities.stream().filter(e -> entity.getService().equals(e.getService()))
                    .findFirst().orElse(null);

            if (entityFromFile != null) {
                entities.remove(entityFromFile);

                entity.setConfigurations(entityFromFile.getConfigurations());
            } else {
                entity.setConfigurations(configurations.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                            Configuration configuration = new Configuration();
                            configuration.setDescription(entry.getValue());

                            return configuration;
                        })));
            }

            entities.add(entity);
            String marshalledServiceTarget = wanakuMarshallerService.marshal(entities);

            tryWrite(file, marshalledServiceTarget.getBytes());
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + file.toString(), e);
        }
    }

    @Override
    public void deregister(String service, ServiceType serviceType) {
        LOG.tracef("Not de-registering service %s because it is file-based and entries should persist",
                service);
    }

    @Override
    public Service getService(String service) {
        try {
            String data = tryRead(file);
            List<ServiceTargetEntity> entities = wanakuMarshallerService.unmarshal(data, ServiceTargetEntity.class);

            ServiceTargetEntity entity = entities.stream().filter(e -> service.equals(e.getService()))
                    .findFirst().orElseThrow(() -> new ServiceNotFoundException("Service with id: " + service + " not found"));

            return toService(entity);
        } catch (IOException e) {
            throw new WanakuException("Can't read file " + file.toString(), e);
        }
    }

    private static Service toService(ServiceTargetEntity entity) {
        Service model = new Service();

        Configurations configurations = new Configurations();
        configurations.setConfigurations(entity.getConfigurations());
        model.setConfigurations(configurations);

        model.setTarget(entity.toAddress());
        return model;
    }

    @Override
    public void saveState(String service, boolean healthy, String message) {
        String fileName = String.format("state-%s.json", service);
        try {
            if (!Files.exists(folder.resolve(fileName))) {
                Files.createFile(folder.resolve(fileName));
                Files.write(folder.resolve(fileName), "[]".getBytes());
            }
            String data = tryRead(folder.resolve(fileName));

            List<State> states = wanakuMarshallerService.unmarshal(data, State.class);

            states.add(new State(service, healthy, message));

            Files.deleteIfExists(folder.resolve(fileName));
            Files.write(folder.resolve(fileName), wanakuMarshallerService.marshal(states).getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + folder.resolve(fileName), e);
        }
    }

    @Override
    public List<State> getState(String service, int count) {
        String fileName = String.format("state-%s.json", service);
        try {
            String data = tryRead(folder.resolve(fileName));

            return wanakuMarshallerService.unmarshal(data, State.class);
        } catch (IOException e) {
            throw new WanakuException("Can't read file " + folder.resolve(fileName), e);
        }
    }

    @Override
    public Map<String, Service> getEntries(ServiceType serviceType) {
        try {
            String data = Files.readString(file);

            List<ServiceTargetEntity> entities = wanakuMarshallerService.unmarshal(data, ServiceTargetEntity.class);

            return entities.stream()
                    .filter(entity -> serviceType.equals(entity.getServiceType()))
                    .collect(Collectors.toMap(ServiceTarget::getService, FileServiceRegistry::toService));
        } catch (IOException e) {
            throw new WanakuException("Can't read file " + file, e);
        }
    }

    @Override
    public void update(String target, String option, String value) {
        try {
            String data = tryRead(file);

            List<ServiceTargetEntity> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());

            ServiceTargetEntity entity = entities.stream().filter(e -> target.equals(e.getService()))
                    .findFirst().orElseThrow(() -> new ServiceNotFoundException("Service with id: " + target + " not found"));
            entities.remove(entity);

            entity.getConfigurations().entrySet().stream()
                    .filter(entry -> option.equals(entry.getKey()))
                    .findFirst()
                    .ifPresent(entry -> entry.getValue().setValue(value));

            entities.add(entity);
            String marshalledServiceTarget = wanakuMarshallerService.marshal(entities);

            tryWrite(file, marshalledServiceTarget.getBytes());
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + file.toString(), e);
        }
    }

    @Override
    Class<ServiceTargetEntity> getEntityClass() {
        return ServiceTargetEntity.class;
    }

    @Override
    public ServiceTargetEntity convertToEntity(ServiceTarget model) {
        return new ServiceTargetEntity(model.getService(), model.getHost(), model.getPort(), model.getServiceType());
    }

    @Override
    public ServiceTarget convertToModel(ServiceTargetEntity entity) {
        return new ServiceTarget(entity.getService(), entity.getHost(), entity.getPort(), entity.getServiceType());
    }
}

package ai.wanaku.core.persistence.file;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.persistence.WanakuMarshallerService;
import ai.wanaku.core.persistence.api.WanakuRepository;
import ai.wanaku.core.persistence.types.IdEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class AbstractFileRepository<A, T extends IdEntity, K> implements WanakuRepository<A, T, K> {

    protected WanakuMarshallerService wanakuMarshallerService;
    protected Path file;

    public AbstractFileRepository(WanakuMarshallerService wanakuMarshallerService, Path file) {
        this.wanakuMarshallerService = wanakuMarshallerService;
        this.file = file;

        if (!Files.exists(file)) {
            try {
                Files.createFile(file);
                Files.write(file, "[]".getBytes());
            } catch (IOException e) {
                throw new WanakuException("Can't write to file " + file, e);
            }
        }
    }

    @Override
    public void persist(A model) {
        T entity = convertToEntity(model);

        try {
            String data = Files.readString(file);

            List<T> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());
            Optional<T> rf = entities.stream()
                    .filter(e -> e.getId().equals(entity.getId()))
                    .findFirst();
            if (rf.isPresent()) {
                throw new WanakuException("Resource " + entity.getId() + "  already exists");
            }

            entities.add(entity);
            String marshalledEntity = wanakuMarshallerService.marshal(entities);

            Files.deleteIfExists(file);
            Files.write(file, marshalledEntity.getBytes(), StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new WanakuException("Can't write to file " + file.toString(), e);
        }
    }

    @Override
    public List<A> listAll() {
        String data;
        try {
            data = Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Can't read file " + file.toString(), e);
        }

        return convertToModels(wanakuMarshallerService.unmarshal(data, getEntityClass()));
    }

    @Override
    public boolean deleteById(K id) {
        try {
            String data = Files.readString(file);

            List<T> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());
            int originalSize = entities.size();

            List<T> result = entities.stream()
                    .filter(e -> !e.getId().equals(id))
                    .collect(Collectors.toList());

            String marshalledEntity = wanakuMarshallerService.marshal(result);

            Files.deleteIfExists(file);
            Files.write(file, marshalledEntity.getBytes(), StandardOpenOption.CREATE);

            return originalSize != result.size();
        } catch (IOException e) {
            throw new WanakuException("Error during deletion", e);
        }
    }

    @Override
    public A findById(K id) {
        try {
            String data = Files.readString(file);

            List<T> entities = wanakuMarshallerService.unmarshal(data, getEntityClass());

            Optional<T> result = entities.stream()
                    .filter(e -> e.getId().equals(id))
                    .findFirst();

            if (result.isPresent()) {
                return convertToModel(result.get());
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new WanakuException("Can't retrieve resource " + id, e);
        }
    }

    abstract Class<T> getEntityClass();
}

# Wanaku Persistence Architecture

This document describes the Wanaku persistence layer architecture, designed as a reference for implementing similar persistence systems.

## Overview

The persistence layer follows a layered architecture:

```
┌─────────────────────────────────────┐
│         Service Layer               │
├─────────────────────────────────────┤
│       Repository Interfaces         │  ← API module
├─────────────────────────────────────┤
│    Abstract Implementations         │  ← Infinispan module
├─────────────────────────────────────┤
│     Concrete Repositories           │
├─────────────────────────────────────┤
│    Infinispan Embedded Cache        │
├─────────────────────────────────────┤
│      SingleFileStore (Disk)         │
└─────────────────────────────────────┘
```

**Key characteristics:**
- Repository pattern with generics
- Infinispan embedded cache with file-based persistence
- Protocol Buffers (proto3) for serialization
- CDI for dependency injection
- Thread-safe operations with ReentrantLock

## Entity Hierarchy

### Base Interface: WanakuEntity

All persisted entities implement `WanakuEntity<K>`:

```java
public interface WanakuEntity<K> {
    K getId();
    void setId(K id);
}
```

### Labels Support: LabelsAwareEntity

Entities requiring metadata labels extend `LabelsAwareEntity<T>`:

```java
public abstract class LabelsAwareEntity<T> implements WanakuEntity<T> {
    private Map<String, String> labels;

    public Map<String, String> getLabels();
    public void setLabels(Map<String, String> labels);
    public void addLabel(String key, String value);
    public void addLabels(Map<String, String> labelsMap);
    public String getLabelValue(String labelKey);
    public boolean hasLabel(String labelKey);
    public boolean hasLabel(String labelKey, String labelValue);
    public boolean removeLabel(String labelKey);
}
```

### Concrete Entity Example

```java
public class DataStore extends LabelsAwareEntity<String> {
    private String id;
    private String name;
    private String data;  // Base64-encoded content

    // getters/setters
}
```

## Repository Interfaces

### Base Repository: WanakuRepository

```java
public interface WanakuRepository<A extends WanakuEntity, C> {
    A persist(A entity);
    List<A> listAll();
    boolean deleteById(C id);
    A findById(C id);
    boolean update(C id, A entity);
    boolean remove(Predicate<A> matching);
    int size();
    int removeByField(String fieldName, Object fieldValue);
    int removeByFields(Map<String, Object> fields);
    int removeAll();
    boolean exists(C key);
}
```

**Key operations:**
- `persist()` - Generates ID if null, stores entity
- `removeByField()/removeByFields()` - Bulk deletion using Ickle queries
- `update()` - Lock-protected entity modification

### Label-Aware Repository: LabelAwareInfinispanRepository

```java
public interface LabelAwareInfinispanRepository<A extends LabelsAwareEntity<K>, K>
    extends WanakuRepository<A, K> {

    List<A> findAllFilterByLabelExpression(String labelExpression);
    int removeIf(String labelExpression) throws LabelExpressionParseException;
}
```

**Label expression examples:**
- `category=weather` - Exact match
- `category=weather & !action=forecast` - AND with negation
- `(category=weather | category=news) & environment=production` - Complex boolean

### Domain-Specific Interfaces

```java
public interface DataStoreRepository
    extends LabelAwareInfinispanRepository<DataStore, String> {
    List<DataStore> findByName(String name);
}
```

## Abstract Implementations

### AbstractInfinispanRepository

Base implementation providing thread-safe CRUD operations:

```java
public abstract class AbstractInfinispanRepository<A extends WanakuEntity<K>, K>
    implements WanakuRepository<A, K> {

    protected final EmbeddedCacheManager cacheManager;
    private final ReentrantLock lock = new ReentrantLock();

    protected abstract Class<A> entityType();
    protected abstract String entityName();
    protected abstract K newId();

    @Override
    public A persist(A entity) {
        lock.lock();
        try {
            if (entity.getId() == null) {
                entity.setId(newId());
            }
            getCache().put(entity.getId(), entity);
            return entity;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean update(K id, A entity) {
        lock.lock();
        try {
            if (!getCache().containsKey(id)) {
                return false;
            }
            entity.setId(id);
            getCache().put(id, entity);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public int removeByField(String fieldName, Object fieldValue) {
        // Uses Ickle query: DELETE FROM EntityClass WHERE field = :value
    }

    protected Cache<K, A> getCache() {
        return cacheManager.getCache(entityName());
    }
}
```

### AbstractLabelAwareInfinispanRepository

Extends base with label filtering:

```java
public abstract class AbstractLabelAwareInfinispanRepository<A extends LabelsAwareEntity<K>, K>
    extends AbstractInfinispanRepository<A, K>
    implements LabelAwareInfinispanRepository<A, K> {

    @Override
    public List<A> findAllFilterByLabelExpression(String labelExpression) {
        Predicate<A> predicate = LabelExpressionParser.parse(labelExpression);
        return listAll().stream()
            .filter(predicate)
            .collect(Collectors.toList());
    }

    @Override
    public int removeIf(String labelExpression) {
        Predicate<A> predicate = LabelExpressionParser.parse(labelExpression);
        List<A> toRemove = listAll().stream()
            .filter(predicate)
            .collect(Collectors.toList());
        toRemove.forEach(e -> deleteById(e.getId()));
        return toRemove.size();
    }
}
```

## Concrete Repository Implementation

```java
public class InfinispanDataStoreRepository
    extends AbstractLabelAwareInfinispanRepository<DataStore, String>
    implements DataStoreRepository {

    public InfinispanDataStoreRepository(EmbeddedCacheManager cacheManager,
                                          Configuration configuration) {
        super(cacheManager, configuration);
    }

    @Override
    protected String entityName() {
        return "datastore";
    }

    @Override
    protected Class<DataStore> entityType() {
        return DataStore.class;
    }

    @Override
    protected String newId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public List<DataStore> findByName(String name) {
        QueryFactory queryFactory = Search.getQueryFactory(getCache());
        Query<DataStore> query = queryFactory.create(
            "from ai.wanaku.capabilities.sdk.api.types.DataStore d where d.name = :name");
        query.setParameter("name", name);
        return query.execute().list();
    }
}
```

## Serialization Layer

### Proto Schema Definition

Create `.proto` files in `src/main/resources/proto/`:

```protobuf
// data_store.proto
syntax = "proto3";
package ai.wanaku.capabilities.sdk.api.types;

message DataStore {
  string id = 1;
  string name = 2;
  string data = 3;
  map<string, string> labels = 4;
}
```

**Field type mappings:**
- Strings → `string`
- Labels → `map<string, string>`
- Nested objects → `message`
- Lists → `repeated`

### Marshaller Implementation

Implement `MessageMarshaller<T>` for each entity:

```java
public class DataStoreMarshaller implements MessageMarshaller<DataStore> {

    @Override
    public String getTypeName() {
        return DataStore.class.getCanonicalName();
    }

    @Override
    public Class<? extends DataStore> getJavaClass() {
        return DataStore.class;
    }

    @Override
    public DataStore readFrom(ProtoStreamReader reader) throws IOException {
        DataStore dataStore = new DataStore();
        dataStore.setId(reader.readString("id"));
        dataStore.setName(reader.readString("name"));
        dataStore.setData(reader.readString("data"));
        dataStore.setLabels(reader.readMap("labels",
            new HashMap<>(), String.class, String.class));
        return dataStore;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, DataStore dataStore)
            throws IOException {
        writer.writeString("id", dataStore.getId());
        writer.writeString("name", dataStore.getName());
        writer.writeString("data", dataStore.getData());
        writer.writeMap("labels", dataStore.getLabels(),
            String.class, String.class);
    }
}
```

### Schema Initializer

Register schemas and marshallers:

```java
public class DataStoreSchema
    extends AbstractWanakuSerializationContextInitializer {

    @Override
    public String getProtoFileName() {
        return "data_store.proto";
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        serCtx.registerMarshaller(new DataStoreMarshaller());
    }
}
```

**Base class loads proto file from classpath:**

```java
public abstract class AbstractWanakuSerializationContextInitializer
    implements SerializationContextInitializer {

    public abstract String getProtoFileName();

    public String getProtoFile() {
        return ResourceUtils.getResourceAsString(
            getClass(), "/proto/" + getProtoFileName());
    }

    public void registerSchema(SerializationContext serCtx) {
        serCtx.registerProtoFiles(
            FileDescriptorSource.fromString(getProtoFileName(), getProtoFile()));
    }

    public abstract void registerMarshallers(SerializationContext serCtx);
}
```

## CDI Configuration

### Infinispan Configuration Provider

```java
public class InfinispanConfigurationProvider {

    @ConfigProperty(name = "wanaku.persistence.infinispan.base-folder",
                   defaultValue = "${user.home}/.wanaku/router/")
    String baseFolder;

    @Produces
    Configuration newConfiguration() {
        String location = baseFolder.replace(
            "${user.home}", System.getProperty("user.home"));
        Files.createDirectories(Paths.get(location));

        return new ConfigurationBuilder()
            .clustering()
            .cacheMode(CacheMode.LOCAL)
            .persistence()
            .passivation(false)
            .addStore(SingleFileStoreConfigurationBuilder.class)
            .location(location)
            .build();
    }
}
```

**Configuration options:**
- `CacheMode.LOCAL` - Single-node caching
- `SingleFileStore` - File-based persistence
- `passivation(false)` - All entries persisted to disk
- Configurable storage location via property

### Repository Producer

```java
public class InfinispanPersistenceConfiguration {

    @Inject
    EmbeddedCacheManager cacheManager;

    @Inject
    Configuration configuration;

    @Produces
    DataStoreRepository dataStoreRepository() {
        return new InfinispanDataStoreRepository(cacheManager, configuration);
    }

    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new InfinispanToolReferenceRepository(cacheManager, configuration);
    }

    // Additional producers for each repository type
}
```

## Usage Patterns

### Injecting Repositories

```java
@ApplicationScoped
public class DataStoreService {

    @Inject
    DataStoreRepository repository;

    public DataStore create(String name, String data) {
        DataStore store = new DataStore();
        store.setName(name);
        store.setData(Base64.getEncoder().encodeToString(data.getBytes()));
        store.addLabel("type", "config");
        return repository.persist(store);
    }

    public List<DataStore> findByLabel(String expression) {
        return repository.findAllFilterByLabelExpression(expression);
    }
}
```

### Label-Based Queries

```java
// Find all with specific label
List<ToolReference> tools = toolRepo.findAllFilterByLabelExpression("category=weather");

// Complex expression with AND/OR/NOT
List<ResourceReference> resources = resourceRepo.findAllFilterByLabelExpression(
    "(type=file | type=database) & !environment=test");

// Remove by label expression
int removed = toolRepo.removeIf("deprecated=true & !protected=true");
```

### Bulk Operations

```java
// Remove by single field
int count = repository.removeByField("namespace", "old-namespace");

// Remove by multiple fields (AND condition)
Map<String, Object> fields = Map.of(
    "namespace", "test",
    "type", "temporary"
);
int count = repository.removeByFields(fields);
```

## Directory Structure

```
core/core-persistence/
├── core-persistence-api/
│   └── src/main/java/ai/wanaku/core/persistence/api/
│       ├── WanakuRepository.java
│       ├── LabelAwareInfinispanRepository.java
│       └── {Domain}Repository.java
│
└── core-persistence-infinispan/
    └── src/main/
        ├── java/ai/wanaku/core/persistence/infinispan/
        │   ├── AbstractInfinispanRepository.java
        │   ├── AbstractLabelAwareInfinispanRepository.java
        │   ├── Infinispan{Domain}Repository.java
        │   ├── InfinispanPersistenceConfiguration.java
        │   ├── providers/
        │   │   └── InfinispanConfigurationProvider.java
        │   └── protostream/
        │       ├── schema/
        │       │   ├── AbstractWanakuSerializationContextInitializer.java
        │       │   └── {Domain}Schema.java
        │       └── marshaller/
        │           └── {Domain}Marshaller.java
        └── resources/proto/
            └── {domain}.proto
```

## Implementation Checklist

When adding a new entity type:

1. **Entity Class**
   - [ ] Implement `WanakuEntity<K>` or extend `LabelsAwareEntity<T>`
   - [ ] Define fields with getters/setters

2. **Repository Interface**
   - [ ] Extend `WanakuRepository` or `LabelAwareInfinispanRepository`
   - [ ] Add domain-specific query methods

3. **Repository Implementation**
   - [ ] Extend appropriate abstract class
   - [ ] Implement `entityName()`, `entityType()`, `newId()`
   - [ ] Implement domain-specific query methods

4. **Proto Schema**
   - [ ] Create `.proto` file in `resources/proto/`
   - [ ] Define message with field numbers

5. **Marshaller**
   - [ ] Implement `MessageMarshaller<T>`
   - [ ] Handle all fields in `readFrom()` and `writeTo()`

6. **Schema Initializer**
   - [ ] Extend `AbstractWanakuSerializationContextInitializer`
   - [ ] Return proto filename
   - [ ] Register marshaller

7. **CDI Producer**
   - [ ] Add `@Produces` method in configuration class

## Key Design Decisions

1. **Embedded Cache**: Uses Infinispan embedded mode for simplicity and zero external dependencies. Data persists to local file system.

2. **Local Cache Mode**: Single-node operation. For distributed scenarios, change to `CacheMode.DIST_SYNC` or `REPL_SYNC`.

3. **ReentrantLock**: Ensures thread-safe ID generation and updates. Lock is held only during cache operations.

4. **Ickle Queries**: Infinispan's query language for bulk operations. More efficient than iterating and removing individually.

5. **Label Expressions**: In-memory filtering with parsed predicates. Suitable for moderate data sizes; consider indexed queries for large datasets.

6. **Proto3 Serialization**: Efficient binary format with forward/backward compatibility. Field numbers must not change once deployed.

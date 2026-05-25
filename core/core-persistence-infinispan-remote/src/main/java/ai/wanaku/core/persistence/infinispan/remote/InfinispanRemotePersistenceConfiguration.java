package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.internal.InternalCacheNames;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.SerializationContext;
import ai.wanaku.core.persistence.api.DataStoreRepository;
import ai.wanaku.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.core.persistence.api.NamespaceRepository;
import ai.wanaku.core.persistence.api.PromptReferenceRepository;
import ai.wanaku.core.persistence.api.ResourceReferenceRepository;
import ai.wanaku.core.persistence.api.ToolReferenceRepository;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.AbstractWanakuSerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.ContentSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.DataStoreSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.ForwardReferenceSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.InputSchemaSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.NamespaceSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.PromptReferenceSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.PropertySchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.ResourceReferenceSchema;
import ai.wanaku.core.persistence.infinispan.remote.protostream.schema.ToolReferenceSchema;

@ApplicationScoped
public class InfinispanRemotePersistenceConfiguration {

    @Inject
    RemoteCacheManager cacheManager;

    @PostConstruct
    void init() {
        ProtoStreamMarshaller protoMarshaller = (ProtoStreamMarshaller) cacheManager.getMarshaller();
        SerializationContext serCtx = protoMarshaller.getSerializationContext();

        PropertySchema propertySchema = new PropertySchema();
        propertySchema.registerSchema(serCtx);
        propertySchema.registerMarshallers(serCtx);

        InputSchemaSchema inputSchemaSchema = new InputSchemaSchema();
        inputSchemaSchema.registerSchema(serCtx);
        inputSchemaSchema.registerMarshallers(serCtx);

        ResourceReferenceSchema resourceReferenceSchema = new ResourceReferenceSchema();
        resourceReferenceSchema.registerSchema(serCtx);
        resourceReferenceSchema.registerMarshallers(serCtx);

        ContentSchema contentSchema = new ContentSchema();
        contentSchema.registerSchema(serCtx);
        contentSchema.registerMarshallers(serCtx);

        ToolReferenceSchema toolReferenceSchema = new ToolReferenceSchema();
        toolReferenceSchema.registerSchema(serCtx);
        toolReferenceSchema.registerMarshallers(serCtx);

        PromptReferenceSchema promptReferenceSchema = new PromptReferenceSchema();
        promptReferenceSchema.registerSchema(serCtx);
        promptReferenceSchema.registerMarshallers(serCtx);

        NamespaceSchema namespaceSchema = new NamespaceSchema();
        namespaceSchema.registerSchema(serCtx);
        namespaceSchema.registerMarshallers(serCtx);

        DataStoreSchema dataStoreSchema = new DataStoreSchema();
        dataStoreSchema.registerSchema(serCtx);
        dataStoreSchema.registerMarshallers(serCtx);

        ForwardReferenceSchema forwardReferenceSchema = new ForwardReferenceSchema();
        forwardReferenceSchema.registerSchema(serCtx);
        forwardReferenceSchema.registerMarshallers(serCtx);

        registerSchemasOnServer(
                propertySchema,
                inputSchemaSchema,
                resourceReferenceSchema,
                contentSchema,
                toolReferenceSchema,
                promptReferenceSchema,
                namespaceSchema,
                dataStoreSchema,
                forwardReferenceSchema);
    }

    private void registerSchemasOnServer(AbstractWanakuSerializationContextInitializer... schemas) {
        RemoteCache<String, String> protobufMetadataCache =
                cacheManager.getCache(InternalCacheNames.PROTOBUF_METADATA_CACHE_NAME);
        for (var schema : schemas) {
            protobufMetadataCache.put(schema.getName(), schema.getContent());
        }
    }

    @Produces
    ResourceReferenceRepository resourceReferenceRepository() {
        return new InfinispanRemoteResourceReferenceRepository(cacheManager);
    }

    @Produces
    ToolReferenceRepository toolReferenceRepository() {
        return new InfinispanRemoteToolReferenceRepository(cacheManager);
    }

    @Produces
    ForwardReferenceRepository forwardReferenceRepository() {
        return new InfinispanRemoteForwardReferenceRepository(cacheManager);
    }

    @Produces
    NamespaceRepository namespaceRepository() {
        return new InfinispanRemoteNamespaceRepository(cacheManager);
    }

    @Produces
    PromptReferenceRepository promptReferenceRepository() {
        return new InfinispanRemotePromptReferenceRepository(cacheManager);
    }

    @Produces
    DataStoreRepository dataStoreRepository() {
        return new InfinispanRemoteDataStoreRepository(cacheManager);
    }
}

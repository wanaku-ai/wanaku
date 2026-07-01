package ai.wanaku.core.persistence.infinispan.remote;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.SerializationContext;
import io.quarkus.runtime.StartupEvent;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.AudioContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.DataStoreMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.EmbeddedResourceMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ForwardReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ImageContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.InputSchemaMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.NamespaceMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ParamMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptArgumentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptMessageMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PropertyMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ResourceReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.TextContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ToolReferenceMarshaller;
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

    public void onStart(@Observes StartupEvent event) {
        System.out.println(">>> onStart: registering marshallers");
        ProtoStreamMarshaller protoMarshaller = (ProtoStreamMarshaller) cacheManager.getMarshaller();
        SerializationContext serCtx = protoMarshaller.getSerializationContext();

        // Register schemas first
        new PropertySchema().registerSchema(serCtx);
        new InputSchemaSchema().registerSchema(serCtx);
        new ResourceReferenceSchema().registerSchema(serCtx);
        new ContentSchema().registerSchema(serCtx);
        new ToolReferenceSchema().registerSchema(serCtx);
        new NamespaceSchema().registerSchema(serCtx);
        new DataStoreSchema().registerSchema(serCtx);
        new ForwardReferenceSchema().registerSchema(serCtx);
        new PromptReferenceSchema().registerSchema(serCtx);

        // Register marshallers
        serCtx.registerMarshaller(new PropertyMarshaller());
        serCtx.registerMarshaller(new InputSchemaMarshaller());
        serCtx.registerMarshaller(new ResourceReferenceMarshaller());
        serCtx.registerMarshaller(new TextContentMarshaller());
        serCtx.registerMarshaller(new ImageContentMarshaller());
        serCtx.registerMarshaller(new AudioContentMarshaller());
        serCtx.registerMarshaller(new EmbeddedResourceMarshaller());
        serCtx.registerMarshaller(new ToolReferenceMarshaller());
        serCtx.registerMarshaller(new PromptArgumentMarshaller());
        serCtx.registerMarshaller(new PromptMessageMarshaller());
        serCtx.registerMarshaller(new PromptReferenceMarshaller());
        serCtx.registerMarshaller(new NamespaceMarshaller());
        serCtx.registerMarshaller(new DataStoreMarshaller());
        serCtx.registerMarshaller(new ForwardReferenceMarshaller());
        serCtx.registerMarshaller(new PromptContentMarshaller());
        serCtx.registerMarshaller(new ParamMarshaller());

        // Upload proto files
        var protoCache = cacheManager.getCache("___protobuf_metadata");
        protoCache.put("property.proto", new PropertySchema().getContent());
        protoCache.put("input_schema.proto", new InputSchemaSchema().getContent());
        protoCache.put("resource_reference.proto", new ResourceReferenceSchema().getContent());
        protoCache.put("content.proto", new ContentSchema().getContent());
        protoCache.put("tool_reference.proto", new ToolReferenceSchema().getContent());
        protoCache.put("namespace.proto", new NamespaceSchema().getContent());
        protoCache.put("data_store.proto", new DataStoreSchema().getContent());
        protoCache.put("forward_reference.proto", new ForwardReferenceSchema().getContent());
        protoCache.put("prompt_reference.proto", new PromptReferenceSchema().getContent());

        System.out.println(">>> Marshallers and schemas registered successfully");
    }
}

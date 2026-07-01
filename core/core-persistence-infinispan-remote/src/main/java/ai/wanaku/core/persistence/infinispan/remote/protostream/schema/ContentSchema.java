package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.AudioContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.EmbeddedResourceMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.ImageContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptContentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.TextContentMarshaller;

public class ContentSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    private final ResourceReferenceSchema resourceReferenceSchema = new ResourceReferenceSchema();

    @Override
    public String getName() {
        return "content.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        resourceReferenceSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        resourceReferenceSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new TextContentMarshaller());
        serCtx.registerMarshaller(new ImageContentMarshaller());
        serCtx.registerMarshaller(new AudioContentMarshaller());
        serCtx.registerMarshaller(new EmbeddedResourceMarshaller());
        serCtx.registerMarshaller(new PromptContentMarshaller());
    }
}

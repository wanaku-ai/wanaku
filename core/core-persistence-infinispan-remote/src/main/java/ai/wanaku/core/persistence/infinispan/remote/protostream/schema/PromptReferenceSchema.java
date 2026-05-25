package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptArgumentMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptMessageMarshaller;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptReferenceMarshaller;

public class PromptReferenceSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    private final ContentSchema contentSchema = new ContentSchema();

    @Override
    public String getName() {
        return "prompt_reference.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        contentSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getName(), this.getContent()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        contentSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new PromptArgumentMarshaller());
        serCtx.registerMarshaller(new PromptMessageMarshaller());
        serCtx.registerMarshaller(new PromptReferenceMarshaller());
    }
}

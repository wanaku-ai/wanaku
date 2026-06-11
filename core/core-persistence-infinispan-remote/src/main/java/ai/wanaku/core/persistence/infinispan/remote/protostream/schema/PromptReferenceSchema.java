package ai.wanaku.core.persistence.infinispan.remote.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller.PromptArgumentMarshaller;

public class PromptReferenceSchema extends AbstractWanakuSerializationContextInitializer
        implements SerializationContextInitializer {

    private final ContentSchema contentSchema = new ContentSchema();

    public PromptReferenceSchema() {
        System.out.println(">>> [PromptReferenceSchema] constructed");
    }

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
        // Only register PromptArgumentMarshaller here, others come from SDK
        serCtx.registerMarshaller(new PromptArgumentMarshaller());
    }
}

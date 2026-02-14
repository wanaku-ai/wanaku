package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PromptArgumentMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PromptMessageMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PromptReferenceMarshaller;

public class PromptReferenceSchema extends AbstractWanakuSerializationContextInitializer {

    private static final Logger logger = LoggerFactory.getLogger(PromptReferenceSchema.class);

    public PromptReferenceSchema() {
        logger.info("PromptReferenceSchema instantiated!");
    }

    private final ContentSchema contentSchema = new ContentSchema();

    @Override
    public String getProtoFileName() {
        return "prompt_reference.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        contentSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        contentSchema.registerMarshallers(serCtx);
        // PromptContent is handled via WrappedMessage in PromptMessageMarshaller
        serCtx.registerMarshaller(new PromptArgumentMarshaller());
        serCtx.registerMarshaller(new PromptMessageMarshaller());
        serCtx.registerMarshaller(new PromptReferenceMarshaller());
    }
}

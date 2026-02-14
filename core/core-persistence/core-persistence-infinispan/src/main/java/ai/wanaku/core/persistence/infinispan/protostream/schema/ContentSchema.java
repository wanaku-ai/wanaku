package ai.wanaku.core.persistence.infinispan.protostream.schema;

import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.SerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.AudioContentMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.EmbeddedResourceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ImageContentMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.TextContentMarshaller;

public class ContentSchema extends AbstractWanakuSerializationContextInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ContentSchema.class);

    public ContentSchema() {
        logger.info("ContentSchema instantiated!");
    }

    private final ResourceReferenceSchema resourceReferenceSchema = new ResourceReferenceSchema();

    @Override
    public String getProtoFileName() {
        return "content.proto";
    }

    @Override
    public void registerSchema(SerializationContext serCtx) {
        resourceReferenceSchema.registerSchema(serCtx);
        serCtx.registerProtoFiles(FileDescriptorSource.fromString(this.getProtoFileName(), this.getProtoFile()));
    }

    @Override
    public void registerMarshallers(SerializationContext serCtx) {
        resourceReferenceSchema.registerMarshallers(serCtx);
        serCtx.registerMarshaller(new TextContentMarshaller());
        serCtx.registerMarshaller(new ImageContentMarshaller());
        serCtx.registerMarshaller(new AudioContentMarshaller());
        serCtx.registerMarshaller(new EmbeddedResourceMarshaller());
    }
}

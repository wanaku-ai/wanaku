package ai.wanaku.core.persistence.infinispan.protostream.initializer;

import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ForwardReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.InputSchemaMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.NamespaceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ParamMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.PropertyMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.RemoteToolReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ResourceReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.ToolReferenceMarshaller;
import ai.wanaku.core.persistence.infinispan.protostream.marshaller.WanakuErrorMarshaller;
import java.util.Arrays;

public class TypesSerializationContextInitializer extends AbstractSerializationContextInitializer {

    private static final String PROTO_FILE_NAME = "types.proto";

    private static final String PROTO_FILE_PATH = "proto/types.proto";

    public TypesSerializationContextInitializer() {
        super(
                PROTO_FILE_NAME,
                PROTO_FILE_PATH,
                Arrays.asList(
                        new ForwardReferenceMarshaller(),
                        new InputSchemaMarshaller(),
                        new PropertyMarshaller(),
                        new RemoteToolReferenceMarshaller(),
                        new ParamMarshaller(),
                        new ResourceReferenceMarshaller(),
                        new WanakuErrorMarshaller(),
                        new ToolReferenceMarshaller(),
                        new NamespaceMarshaller()));
    }
}

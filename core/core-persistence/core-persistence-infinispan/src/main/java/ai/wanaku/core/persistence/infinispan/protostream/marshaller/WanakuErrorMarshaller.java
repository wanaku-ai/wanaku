package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.WanakuError;

public class WanakuErrorMarshaller implements MessageMarshaller<WanakuError> {
    @Override
    public String getTypeName() {
        return WanakuError.class.getCanonicalName();
    }

    @Override
    public Class<? extends WanakuError> getJavaClass() {
        return WanakuError.class;
    }

    @Override
    public WanakuError readFrom(ProtoStreamReader reader) throws IOException {
        return new WanakuError(reader.readString("message"));
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, WanakuError ref) throws IOException {
        writer.writeString("message", ref.message());
    }
}

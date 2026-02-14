package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptArgument;

/**
 * Protostream marshaller for PromptArgument.
 */
public class PromptArgumentMarshaller implements MessageMarshaller<PromptArgument> {

    @Override
    public PromptArgument readFrom(ProtoStreamReader reader) throws IOException {
        PromptArgument argument = new PromptArgument();
        argument.setName(reader.readString("name"));
        argument.setDescription(reader.readString("description"));
        argument.setRequired(reader.readBoolean("required"));
        return argument;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptArgument argument) throws IOException {
        writer.writeString("name", argument.getName());
        writer.writeString("description", argument.getDescription());
        writer.writeBoolean("required", argument.isRequired());
    }

    @Override
    public Class<? extends PromptArgument> getJavaClass() {
        return PromptArgument.class;
    }

    @Override
    public String getTypeName() {
        return "ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptArgument";
    }
}

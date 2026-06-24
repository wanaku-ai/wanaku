package ai.wanaku.backend.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.AudioContent;
import ai.wanaku.capabilities.sdk.api.types.EmbeddedResource;
import ai.wanaku.capabilities.sdk.api.types.ImageContent;
import ai.wanaku.capabilities.sdk.api.types.PromptContent;
import ai.wanaku.capabilities.sdk.api.types.PromptMessage;
import ai.wanaku.capabilities.sdk.api.types.TextContent;

public class PromptMessageMarshaller implements MessageMarshaller<PromptMessage> {

    @Override
    public String getTypeName() {
        return "ai.wanaku.capabilities.sdk.api.types.PromptReference.PromptMessage";
    }

    @Override
    public Class<? extends PromptMessage> getJavaClass() {
        return PromptMessage.class;
    }

    @Override
    public PromptMessage readFrom(ProtoStreamReader reader) throws IOException {
        PromptMessage message = new PromptMessage();
        message.setRole(reader.readString("role"));

        TextContent text = reader.readObject("text_content", TextContent.class);
        if (text != null) {
            message.setContent(text);
            return message;
        }
        ImageContent image = reader.readObject("image_content", ImageContent.class);
        if (image != null) {
            message.setContent(image);
            return message;
        }
        AudioContent audio = reader.readObject("audio_content", AudioContent.class);
        if (audio != null) {
            message.setContent(audio);
            return message;
        }
        EmbeddedResource embedded = reader.readObject("embedded_resource", EmbeddedResource.class);
        if (embedded != null) {
            message.setContent(embedded);
        }

        return message;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptMessage message) throws IOException {
        writer.writeString("role", message.getRole());

        PromptContent content = message.getContent();
        if (content instanceof TextContent textContent) {
            writer.writeObject("text_content", textContent, TextContent.class);
        } else if (content instanceof ImageContent imageContent) {
            writer.writeObject("image_content", imageContent, ImageContent.class);
        } else if (content instanceof AudioContent audioContent) {
            writer.writeObject("audio_content", audioContent, AudioContent.class);
        } else if (content instanceof EmbeddedResource embeddedResource) {
            writer.writeObject("embedded_resource", embeddedResource, EmbeddedResource.class);
        }
    }
}

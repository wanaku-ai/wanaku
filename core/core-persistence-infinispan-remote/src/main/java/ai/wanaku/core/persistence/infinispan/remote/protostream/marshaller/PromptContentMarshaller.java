package ai.wanaku.core.persistence.infinispan.remote.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.AudioContent;
import ai.wanaku.capabilities.sdk.api.types.EmbeddedResource;
import ai.wanaku.capabilities.sdk.api.types.ImageContent;
import ai.wanaku.capabilities.sdk.api.types.PromptContent;
import ai.wanaku.capabilities.sdk.api.types.TextContent;

public class PromptContentMarshaller implements MessageMarshaller<PromptContent> {

    @Override
    public String getTypeName() {
        return "ai.wanaku.capabilities.sdk.api.types.PromptContent";
    }

    @Override
    public Class<? extends PromptContent> getJavaClass() {
        return PromptContent.class;
    }

    @Override
    public PromptContent readFrom(ProtoStreamReader reader) throws IOException {
        try {
            TextContent text = reader.readObject("text_content", TextContent.class);
            if (text != null) return text;
        } catch (Exception e) {
            /* ignore */
        }

        try {
            ImageContent image = reader.readObject("image_content", ImageContent.class);
            if (image != null) return image;
        } catch (Exception e) {
            /* ignore */
        }

        try {
            AudioContent audio = reader.readObject("audio_content", AudioContent.class);
            if (audio != null) return audio;
        } catch (Exception e) {
            /* ignore */
        }

        try {
            EmbeddedResource resource = reader.readObject("embedded_resource", EmbeddedResource.class);
            return resource;
        } catch (Exception e) {
            /* ignore */
        }

        return null;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptContent content) throws IOException {
        if (content == null) return;

        if (content instanceof TextContent) {
            writer.writeObject("text_content", content, TextContent.class);
        } else if (content instanceof ImageContent) {
            writer.writeObject("image_content", content, ImageContent.class);
        } else if (content instanceof AudioContent) {
            writer.writeObject("audio_content", content, AudioContent.class);
        } else if (content instanceof EmbeddedResource) {
            writer.writeObject("embedded_resource", content, EmbeddedResource.class);
        } else {
            throw new IOException(
                    "Unknown PromptContent subtype: " + content.getClass().getName());
        }
    }
}

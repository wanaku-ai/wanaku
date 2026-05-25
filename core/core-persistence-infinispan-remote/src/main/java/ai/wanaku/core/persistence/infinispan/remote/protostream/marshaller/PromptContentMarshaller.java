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
        String type = reader.readString("type");

        return switch (type) {
            case "text" -> {
                TextContent text = new TextContent();
                text.setText(reader.readString("text"));
                yield text;
            }
            case "image" -> {
                ImageContent image = new ImageContent();
                image.setData(reader.readString("data"));
                image.setMimeType(reader.readString("mime_type"));
                yield image;
            }
            case "audio" -> {
                AudioContent audio = new AudioContent();
                audio.setData(reader.readString("data"));
                audio.setMimeType(reader.readString("mime_type"));
                yield audio;
            }
            case "resource" -> {
                EmbeddedResource embedded = new EmbeddedResource();
                embedded.setResource(
                        reader.readObject("resource", ai.wanaku.capabilities.sdk.api.types.ResourceReference.class));
                yield embedded;
            }
            default -> null;
        };
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, PromptContent content) throws IOException {
        String type = content.getType();
        writer.writeString("type", type);

        switch (type) {
            case "text" -> {
                TextContent text = (TextContent) content;
                writer.writeString("text", text.getText());
            }
            case "image" -> {
                ImageContent image = (ImageContent) content;
                writer.writeString("data", image.getData());
                writer.writeString("mime_type", image.getMimeType());
            }
            case "audio" -> {
                AudioContent audio = (AudioContent) content;
                writer.writeString("data", audio.getData());
                writer.writeString("mime_type", audio.getMimeType());
            }
            case "resource" -> {
                EmbeddedResource embedded = (EmbeddedResource) content;
                writer.writeObject(
                        "resource",
                        embedded.getResource(),
                        ai.wanaku.capabilities.sdk.api.types.ResourceReference.class);
            }
        }
    }
}

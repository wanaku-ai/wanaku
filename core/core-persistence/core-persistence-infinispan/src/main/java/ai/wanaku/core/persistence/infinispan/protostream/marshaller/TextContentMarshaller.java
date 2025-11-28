package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.TextContent;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Protostream marshaller for TextContent.
 */
public class TextContentMarshaller implements MessageMarshaller<TextContent> {

    @Override
    public String getTypeName() {
        return TextContent.class.getCanonicalName();
    }

    @Override
    public Class<? extends TextContent> getJavaClass() {
        return TextContent.class;
    }

    @Override
    public TextContent readFrom(ProtoStreamReader reader) throws IOException {
        TextContent content = new TextContent();
        content.setText(reader.readString("text"));
        return content;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, TextContent content) throws IOException {
        writer.writeString("text", content.getText());
    }
}

package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.ImageContent;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Protostream marshaller for ImageContent.
 */
public class ImageContentMarshaller implements MessageMarshaller<ImageContent> {

    @Override
    public String getTypeName() {
        return ImageContent.class.getCanonicalName();
    }

    @Override
    public Class<? extends ImageContent> getJavaClass() {
        return ImageContent.class;
    }

    @Override
    public ImageContent readFrom(ProtoStreamReader reader) throws IOException {
        ImageContent content = new ImageContent();
        content.setData(reader.readString("data"));
        content.setMimeType(reader.readString("mime_type"));
        return content;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ImageContent content) throws IOException {
        writer.writeString("data", content.getData());
        writer.writeString("mime_type", content.getMimeType());
    }
}

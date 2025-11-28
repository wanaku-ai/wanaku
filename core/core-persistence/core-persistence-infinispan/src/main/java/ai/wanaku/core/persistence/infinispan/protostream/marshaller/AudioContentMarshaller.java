package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.AudioContent;
import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;

/**
 * Protostream marshaller for AudioContent.
 */
public class AudioContentMarshaller implements MessageMarshaller<AudioContent> {

    @Override
    public String getTypeName() {
        return AudioContent.class.getCanonicalName();
    }

    @Override
    public Class<? extends AudioContent> getJavaClass() {
        return AudioContent.class;
    }

    @Override
    public AudioContent readFrom(ProtoStreamReader reader) throws IOException {
        AudioContent content = new AudioContent();
        content.setData(reader.readString("data"));
        content.setMimeType(reader.readString("mime_type"));
        return content;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, AudioContent content) throws IOException {
        writer.writeString("data", content.getData());
        writer.writeString("mime_type", content.getMimeType());
    }
}

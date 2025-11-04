package ai.wanaku.api.types;

import java.util.Objects;

/**
 * Represents audio content in a prompt message.
 * Audio is provided as base64-encoded data with a MIME type.
 */
public class AudioContent implements PromptContent {
    private static final String TYPE = "audio";

    /**
     * Base64-encoded audio data.
     */
    private String data;

    /**
     * MIME type of the audio (e.g., "audio/mp3", "audio/wav", "audio/ogg").
     */
    private String mimeType;

    public AudioContent() {}

    public AudioContent(String data, String mimeType) {
        this.data = data;
        this.mimeType = mimeType;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AudioContent that = (AudioContent) o;
        return Objects.equals(data, that.data) && Objects.equals(mimeType, that.mimeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(data, mimeType);
    }

    @Override
    public String toString() {
        return "AudioContent{" + "type='"
                + TYPE + '\'' + ", mimeType='"
                + mimeType + '\'' + ", dataLength="
                + (data != null ? data.length() : 0) + '}';
    }
}

package ai.wanaku.operator.wanaku;

public class WanakuStatus {
    private String host;
    private String sseEndpoint;
    private String streamableEndpoint;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getSseEndpoint() {
        return sseEndpoint;
    }

    public void setSseEndpoint(String sseEndpoint) {
        this.sseEndpoint = sseEndpoint;
    }

    public String getStreamableEndpoint() {
        return streamableEndpoint;
    }

    public void setStreamableEndpoint(String streamableEndpoint) {
        this.streamableEndpoint = streamableEndpoint;
    }
}

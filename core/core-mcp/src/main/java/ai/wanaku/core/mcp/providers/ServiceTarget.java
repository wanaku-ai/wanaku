package ai.wanaku.core.mcp.providers;

public class ServiceTarget {
    private final String service;
    private final String host;
    private final int port;
    private final ServiceType serviceType;

    private ServiceTarget(String service, String host, int port, ServiceType serviceType) {
        this.service = service;
        this.host = host;
        this.port = port;
        this.serviceType = serviceType;
    }

    public String getService() {
        return service;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public String toAddress() {
        return String.format("%s:%d", host, port);
    }

    public static ServiceTarget provider(String service, String address, int port) {
        return new ServiceTarget(service, address, port, ServiceType.RESOURCE_PROVIDER);
    }

    public static ServiceTarget toolInvoker(String service, String address, int port) {
        return new ServiceTarget(service, address, port, ServiceType.TOOL_INVOKER);
    }
}

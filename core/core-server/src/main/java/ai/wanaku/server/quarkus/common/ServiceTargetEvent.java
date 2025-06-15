package ai.wanaku.server.quarkus.common;

import ai.wanaku.api.types.discovery.ServiceState;
import ai.wanaku.api.types.providers.ServiceTarget;

public class ServiceTargetEvent {
    private EventType eventType;
    private String id;
    public ServiceTarget serviceTarget;
    public ServiceState serviceState;

    private ServiceTargetEvent(EventType eventType, ServiceTarget serviceTarget) {
        this.eventType = eventType;
        this.serviceTarget = serviceTarget;
        this.id = serviceTarget.getId();
    }

    private ServiceTargetEvent(EventType eventType, String id, ServiceState serviceState) {
        this.eventType = eventType;
        this.id = id;
        this.serviceState = serviceState;
    }

    private ServiceTargetEvent(String id) {
        this.eventType = EventType.PING;
        this.id = id;
    }
    
    public static ServiceTargetEvent register(ServiceTarget serviceTarget) {
        return new ServiceTargetEvent(EventType.REGISTER, serviceTarget);
    }

    public static ServiceTargetEvent deregister(ServiceTarget serviceTarget) {
        return new ServiceTargetEvent(EventType.DEREGISTER, serviceTarget);
    }

    public static ServiceTargetEvent update(String id, ServiceState serviceState) {
        return new ServiceTargetEvent(EventType.UPDATE, id, serviceState);
    }

    public static ServiceTargetEvent ping(String id) {
        return new ServiceTargetEvent(id);
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ServiceTarget getServiceTarget() {
        return serviceTarget;
    }

    public void setServiceTarget(ServiceTarget serviceTarget) {
        this.serviceTarget = serviceTarget;
    }
}

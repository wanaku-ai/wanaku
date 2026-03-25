package ai.wanaku.backend.api.v1.capabilities;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.MutinyEmitter;
import ai.wanaku.backend.common.ServiceTargetEvent;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.capabilities.sdk.api.types.discovery.ActivityRecord;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.services.api.FleetStatus;
import ai.wanaku.core.services.api.StaleCapabilityInfo;

@ApplicationScoped
@Path("/api/v1/capabilities")
public class CapabilitiesResource {
    private static final Logger LOG = Logger.getLogger(CapabilitiesResource.class);
    private static final long DEFAULT_MAX_AGE_SECONDS = 86400; // 1 day

    @Inject
    CapabilitiesBean capabilitiesBean;

    @Inject
    @Channel("service-target-event")
    Multi<ServiceTargetEvent> serviceTargetEvents;

    @Inject
    @Channel("service-target-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ServiceTargetEvent> serviceTargetEventEmitter;

    @PostConstruct
    void initialize() {
        // Without this, the first http request fails. This seems to force
        // it to subscribe
        serviceTargetEvents.subscribe().with(events -> {});
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<ServiceTarget>> list() {
        return new WanakuResponse<>(capabilitiesBean.listAllCapabilities());
    }

    @Path("/tools/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, List<ActivityRecord>>> toolsState() {
        return new WanakuResponse<>(capabilitiesBean.toolsState());
    }

    @Path("/resources/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, List<ActivityRecord>>> resourcesState() {
        return new WanakuResponse<>(capabilitiesBean.resourcesState());
    }

    @Path("/notifications")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Transactional
    @GET
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> targetsEventStream(@Context Sse sse) {
        return serviceTargetEvents.map(event -> sse.newEventBuilder()
                .name(event.getEventType().name())
                .id(
                        event.getId() != null
                                ? event.getId()
                                : event.getServiceTarget().getId())
                .data(event)
                .build());
    }

    @Path("/stale")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<List<StaleCapabilityInfo>> listStale(
            @QueryParam("maxAgeSeconds") Long maxAgeSeconds, @QueryParam("inactiveOnly") Boolean inactiveOnly) {
        long ageSeconds = maxAgeSeconds != null ? maxAgeSeconds : DEFAULT_MAX_AGE_SECONDS;
        boolean inactive = inactiveOnly != null && inactiveOnly;

        List<StaleCapabilityInfo> staleCapabilities = capabilitiesBean.listStaleCapabilities(ageSeconds, inactive);
        return new WanakuResponse<>(staleCapabilities);
    }

    @Path("/stale")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Integer> cleanupStale(
            @QueryParam("maxAgeSeconds") Long maxAgeSeconds, @QueryParam("inactiveOnly") Boolean inactiveOnly) {
        long ageSeconds = maxAgeSeconds != null ? maxAgeSeconds : DEFAULT_MAX_AGE_SECONDS;
        boolean inactive = inactiveOnly != null && inactiveOnly;

        List<ServiceTarget> removedTargets = capabilitiesBean.cleanupStaleCapabilities(ageSeconds, inactive);

        // Emit deregister events for each removed capability
        for (ServiceTarget target : removedTargets) {
            serviceTargetEventEmitter.send(ServiceTargetEvent.deregister(target));
        }

        return new WanakuResponse<>(removedTargets.size());
    }

    @Path("/fleet/status")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<FleetStatus> fleetStatus() {
        return new WanakuResponse<>(capabilitiesBean.getFleetStatus());
    }
}

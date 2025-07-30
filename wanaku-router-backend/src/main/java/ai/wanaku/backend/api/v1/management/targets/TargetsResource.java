package ai.wanaku.backend.api.v1.management.targets;

import java.util.List;
import java.util.Map;

import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.api.types.discovery.ActivityRecord;
import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.backend.common.ServiceTargetEvent;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

@ApplicationScoped
@Path("/api/v1/management/targets")
public class TargetsResource {
    private static final Logger LOG = Logger.getLogger(TargetsResource.class);

    @Inject
    TargetsBean targetsBean;

    @Inject
    @Channel("service-target-event")
    Multi<ServiceTargetEvent> serviceTargetEvents;

    @PostConstruct
    void initialize() {
        // Without this, the first http request fails. This seems to force
        // it to subscribe
        serviceTargetEvents.subscribe().with(events -> {});
    }

    @Path("/tools/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public WanakuResponse<List<ServiceTarget>> toolList() {
        return new WanakuResponse<>(targetsBean.toolList());
    }

    @Path("/tools/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, List<ActivityRecord>>> toolsState() {
        return new WanakuResponse<>(targetsBean.toolsState());
    }

    @Path("/resources/list")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    public WanakuResponse<List<ServiceTarget>> resourcesList() {
        return new WanakuResponse<>(targetsBean.resourcesList());
    }

    @Path("/resources/state")
    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public WanakuResponse<Map<String, List<ActivityRecord>>> resourcesState() {
        return new WanakuResponse<>(targetsBean.resourcesState());
    }

    @Path("/notifications")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Transactional
    @GET
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> targetsEventStream(@Context Sse sse) {
        return serviceTargetEvents.map(event -> sse.newEventBuilder()
                .name(event.getEventType().name())
                .id(event.getId() != null ? event.getId() : event.getServiceTarget().getId())
                .data(event)
                .build());
    }
}

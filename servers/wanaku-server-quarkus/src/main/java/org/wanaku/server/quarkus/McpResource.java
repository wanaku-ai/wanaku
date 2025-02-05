package org.wanaku.server.quarkus;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import io.smallrye.mutiny.Multi;
import io.smallrye.reactive.messaging.MutinyEmitter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.wanaku.api.types.McpMessage;

@ApplicationScoped
@Path("/")
public class McpResource {
    private static final Logger LOG = Logger.getLogger(McpResource.class);

    @Inject
    @Channel("mcpEvents")
    Multi<McpMessage> events;

    @Inject
    @Channel("mcpNewConnections")
    MutinyEmitter<String> newConnEmitter;

    @Inject
    @Channel("mcpRequests")
    MutinyEmitter<String> requestsEmitter;

    @Inject
    Sse sse;

    @PostConstruct
    void initialize() {
        // Without this, the first http request fails. This seems to force
        // it to subscribe
        LOG.debug("McpResource initialized");
        events.subscribe().with(events -> {});
    }

    @Path("/message")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response request(String request) {
        boolean hasRequests = requestsEmitter.hasRequests();
        if (hasRequests) {
            requestsEmitter.sendAndForget(request);

            return Response.accepted().build();
        } else {
            return Response.status(Response.Status.NO_CONTENT).build();
        }
    }

    @Path("/sse")
    @GET
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> stream() {
        boolean hasRequests = newConnEmitter.hasRequests();

        if (hasRequests) {
            LOG.debug("Emitting new connection request");
            newConnEmitter.sendAndForget("");
        } else {
            LOG.warn("Not enough credits to send the request request");
        }

        return events.map(e -> sse.newEventBuilder()
                .name(e.event)
                .data(e.payload).build());
    }
}

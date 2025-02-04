package org.wanaku.server.quarkus;

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
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.wanaku.server.quarkus.types.McpMessage;

@Path("/")
public class McpResource {
    private static final Logger LOG = Logger.getLogger(McpResource.class);

    @Channel("mcpEvents")
    Multi<McpMessage> events;

    @Inject
    @Channel("mcpNewConnections")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 1000)
    Emitter<String> newConnEmitter;

    @Inject
    @Channel("mcpRequests")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 1000)
    Emitter<String> requestsEmitter;


    @Inject
    Sse sse;

    @Path("/message")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response request(String request) {
        boolean hasRequests = requestsEmitter.hasRequests();
        if (hasRequests) {
            requestsEmitter.send(request);

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
            newConnEmitter.send("");
        }

        return events.map(e -> sse.newEventBuilder()
                .name(e.event)
                .data(e.payload).build());
    }
}

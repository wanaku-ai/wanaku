/*
 * Copyright 2026 Wanaku AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.wanaku.backend.api.v2.toolcalls;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import io.smallrye.mutiny.Multi;
import ai.wanaku.backend.common.ToolCallEvent;

/**
 * JAX-RS REST resource for tool call debugging endpoints.
 * <p>
 * This class implements the {@code /api/v2/tool-calls} endpoints
 * for streaming tool call events via Server-Sent Events (SSE).
 * </p>
 * <p>
 * The API provides endpoints for:
 * </p>
 * <ul>
 *   <li>GET /notifications - Stream all tool call events</li>
 *   <li>GET /notifications/{connectionId} - Stream tool call events for a specific connection</li>
 * </ul>
 * <p>
 * This is an application-scoped CDI bean that serves as the entry point for
 * HTTP requests related to tool call debugging.
 * </p>
 */
@ApplicationScoped
@Path("/api/v2/tool-calls")
public class ToolCallResource {
    private static final Logger LOG = Logger.getLogger(ToolCallResource.class);

    @Inject
    @Channel("tool-call-event")
    Multi<ToolCallEvent> toolCallEvents;

    @PostConstruct
    void initialize() {
        // Without this, the first http request fails. This seems to force
        // it to subscribe
        toolCallEvents.subscribe().with(events -> {});
    }

    /**
     * Streams all tool call events via Server-Sent Events (SSE).
     * <p>
     * This endpoint serves as the SSE endpoint that streams all tool call events
     * to subscribed clients in real-time.
     * </p>
     * <p>
     * The response includes proper SSE headers:
     * </p>
     * <ul>
     *   <li>Content-Type: text/event-stream</li>
     *   <li>Cache-Control: no-cache</li>
     *   <li>Connection: keep-alive</li>
     *   <li>X-Accel-Buffering: no</li>
     * </ul>
     *
     * @param sse the SSE context for creating events
     * @return a stream of tool call events
     */
    @GET
    @Path("/notifications")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> streamAllToolCalls(@Context Sse sse) {
        LOG.info("SSE connection established for all tool call events");

        return toolCallEvents.map(event -> sse.newEventBuilder()
                .id(event.getEventId())
                .name(event.getEventType().asValue())
                .data(event)
                .build());
    }

    /**
     * Streams tool call events for a specific connection via Server-Sent Events (SSE).
     * <p>
     * This endpoint serves as the SSE endpoint that streams tool call events
     * filtered by connection ID to subscribed clients in real-time.
     * </p>
     *
     * @param connectionId the connection ID to filter events by
     * @param sse the SSE context for creating events
     * @return a stream of filtered tool call events
     */
    @GET
    @Path("/notifications/{connectionId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> streamToolCallsByConnection(
            @PathParam("connectionId") String connectionId, @Context Sse sse) {

        LOG.infof("SSE connection established for tool call events with connectionId: %s", connectionId);

        return toolCallEvents
                .filter(event -> connectionId.equals(event.getConnectionId()))
                .map(event -> sse.newEventBuilder()
                        .id(event.getEventId())
                        .name(event.getEventType().asValue())
                        .data(event)
                        .build());
    }
}

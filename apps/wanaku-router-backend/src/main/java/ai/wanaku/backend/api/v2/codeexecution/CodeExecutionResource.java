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
package ai.wanaku.backend.api.v2.codeexecution;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;
import io.smallrye.mutiny.Multi;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionError;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionEvent;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionResponse;

/**
 * JAX-RS REST resource for code execution endpoints.
 * <p>
 * This class implements the {@code /api/v2/code-execution-engine} endpoints
 * for submitting code for execution and streaming execution results via
 * Server-Sent Events (SSE).
 * </p>
 * <p>
 * The API provides two main endpoints:
 * </p>
 * <ul>
 *   <li>POST /{engine-type}/{language} - Submit code for execution</li>
 *   <li>GET /{engine-type}/{language}/{task-uuid} - Stream execution results via SSE</li>
 * </ul>
 * <p>
 * This is an application-scoped CDI bean that serves as the entry point for
 * HTTP requests related to code execution.
 * </p>
 */
@ApplicationScoped
@Path("/api/v2/code-execution-engine")
public class CodeExecutionResource {
    private static final Logger LOG = Logger.getLogger(CodeExecutionResource.class);

    @Inject
    CodeExecutionBean codeExecutionBean;

    @Inject
    @Channel("code-execution-event")
    Multi<CodeExecutionEvent> codeExecutionEvents;

    @PostConstruct
    void initialize() {
        // Without this, the first http request fails. This seems to force
        // it to subscribe
        codeExecutionEvents.subscribe().with(events -> {});
    }

    /**
     * Submits code for execution.
     * <p>
     * This endpoint accepts code submission, validates the request, generates
     * a random UUID representing the code execution task, and returns HTTP 201
     * Created with a Location header containing the SSE stream endpoint URL.
     * </p>
     * <p>
     * The response body contains the task UUID and the full SSE stream URL in
     * the format: {@code http://host:8080/api/v2/code-execution-engine/{engine-type}/{language}/{task-uuid}}
     * </p>
     *
     * @param engineType the execution engine type (e.g., "jvm", "interpreted", "camel")
     * @param language the programming language (e.g., "java", "python", "yaml", "xml")
     * @param request the code execution request containing the code and parameters
     * @param uriInfo the URI information for building the SSE URL
     * @return HTTP 201 Created with the task details and SSE URL
     */
    @POST
    @Path("/{engineType}/{language}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @org.eclipse.microprofile.openapi.annotations.responses.APIResponses(
            value = {
                @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(
                        responseCode = "201",
                        description = "Created",
                        content =
                                @org.eclipse.microprofile.openapi.annotations.media.Content(
                                        mediaType = MediaType.APPLICATION_JSON,
                                        schema =
                                                @org.eclipse.microprofile.openapi.annotations.media.Schema(
                                                        implementation = CodeExecutionResponse.class))),
                @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content =
                                @org.eclipse.microprofile.openapi.annotations.media.Content(
                                        mediaType = MediaType.APPLICATION_JSON,
                                        schema =
                                                @org.eclipse.microprofile.openapi.annotations.media.Schema(
                                                        implementation = CodeExecutionError.class))),
                @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(
                        responseCode = "500",
                        description = "Internal Server Error",
                        content =
                                @org.eclipse.microprofile.openapi.annotations.media.Content(
                                        mediaType = MediaType.APPLICATION_JSON,
                                        schema =
                                                @org.eclipse.microprofile.openapi.annotations.media.Schema(
                                                        implementation = CodeExecutionError.class)))
            })
    public Response submitCode(
            @PathParam("engineType") String engineType,
            @PathParam("language") String language,
            CodeExecutionRequest request,
            @Context UriInfo uriInfo) {

        LOG.infof("Received code execution request: engine=%s, language=%s", engineType, language);

        try {
            // Validate request
            try {
                request.validate();
            } catch (IllegalArgumentException e) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(new CodeExecutionError("VALIDATION_ERROR", e.getMessage()))
                        .build();
            }

            // Get base URL from request
            String baseUrl = uriInfo.getBaseUri().toString().replaceAll("/$", "");

            // Submit execution and get response
            CodeExecutionResponse response = codeExecutionBean.submitExecution(engineType, language, request, baseUrl);

            // Build Location header
            String locationUrl = response.streamUrl();

            LOG.infof("Code execution task created: %s", response.taskId());

            // Return 201 Created with Location header
            return Response.status(Response.Status.CREATED)
                    .header("Location", locationUrl)
                    .entity(response)
                    .build();

        } catch (Exception e) {
            LOG.errorf(e, "Error submitting code execution: engine=%s, language=%s", engineType, language);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new CodeExecutionError(
                            "INTERNAL_ERROR", "Failed to submit code execution: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Streams execution results via Server-Sent Events (SSE).
     * <p>
     * This endpoint serves as the SSE endpoint that streams execution responses
     * back to the caller for the specified task UUID. The stream automatically
     * closes when execution completes.
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
     * @param engineType the execution engine type
     * @param language the programming language
     * @param taskId the UUID of the execution task
     * @param sse the SSE context for creating events
     */
    @GET
    @Path("/{engineType}/{language}/{taskId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<OutboundSseEvent> streamResults(
            @PathParam("engineType") String engineType,
            @PathParam("language") String language,
            @PathParam("taskId") String taskId,
            @Context Sse sse) {

        LOG.infof("SSE connection established: engine=%s, language=%s, taskId=%s", engineType, language, taskId);

        return codeExecutionEvents
                .filter(event -> taskId.equals(event.getTaskId()))
                .map(event -> sse.newEventBuilder()
                        .name(event.getEventType().name())
                        .id(event.getTaskId())
                        .data(event)
                        .build());
    }
}

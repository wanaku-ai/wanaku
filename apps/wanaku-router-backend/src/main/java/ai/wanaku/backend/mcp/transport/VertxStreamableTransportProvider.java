package ai.wanaku.backend.mcp.transport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jboss.logging.Logger;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransport;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import reactor.core.publisher.Mono;

public class VertxStreamableTransportProvider implements McpStreamableServerTransportProvider {
    private static final Logger LOG = Logger.getLogger(VertxStreamableTransportProvider.class);

    private static final String APPLICATION_JSON = "application/json";
    private static final String TEXT_EVENT_STREAM = "text/event-stream";
    private static final String MESSAGE_EVENT_TYPE = "message";

    private final McpJsonMapper jsonMapper;
    private final ConcurrentHashMap<String, McpStreamableServerSession> sessions = new ConcurrentHashMap<>();
    private volatile McpStreamableServerSession.Factory sessionFactory;
    private volatile boolean isClosing = false;

    public VertxStreamableTransportProvider(McpJsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        if (sessions.isEmpty()) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            sessions.values().parallelStream().forEach(session -> {
                try {
                    session.sendNotification(method, params).block();
                } catch (Exception e) {
                    LOG.debugf("Failed to notify session %s: %s", session.getId(), e.getMessage());
                }
            });
        });
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
        return Mono.defer(() -> {
            McpStreamableServerSession session = sessions.get(sessionId);
            if (session == null) {
                return Mono.empty();
            }
            return session.sendNotification(method, params);
        });
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            isClosing = true;
            sessions.values().parallelStream().forEach(session -> {
                try {
                    session.closeGracefully().block();
                } catch (Exception e) {
                    LOG.warnf("Failed to close session %s: %s", session.getId(), e.getMessage());
                }
            });
            sessions.clear();
        });
    }

    public Handler<RoutingContext> postHandler() {
        return ctx -> handlePost(ctx);
    }

    public Handler<RoutingContext> getHandler() {
        return ctx -> handleGet(ctx);
    }

    public Handler<RoutingContext> deleteHandler() {
        return ctx -> handleDelete(ctx);
    }

    private void handlePost(RoutingContext ctx) {
        if (isClosing) {
            sendError(ctx, 503, "Server is shutting down");
            return;
        }

        HttpServerRequest request = ctx.request();
        String accept = request.getHeader("Accept");

        {
            String body = ctx.body().asString();
            if (body == null || body.isEmpty()) {
                sendError(ctx, 400, "Empty request body");
                return;
            }

            try {
                McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
                McpTransportContext transportContext = extractTransportContext(request);

                if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest
                        && jsonrpcRequest.method().equals(McpSchema.METHOD_INITIALIZE)) {
                    handleInitialize(ctx, jsonrpcRequest);
                    return;
                }

                String sessionId = request.getHeader(HttpHeaders.MCP_SESSION_ID);
                if (sessionId == null || sessionId.isBlank()) {
                    sendMcpError(
                            ctx,
                            400,
                            McpSchema.ErrorCodes.INVALID_REQUEST,
                            "Session ID required in mcp-session-id header");
                    return;
                }

                McpStreamableServerSession session = sessions.get(sessionId);
                if (session == null) {
                    sendMcpError(ctx, 404, McpSchema.ErrorCodes.INTERNAL_ERROR, "Session not found: " + sessionId);
                    return;
                }

                if (message instanceof McpSchema.JSONRPCResponse jsonrpcResponse) {
                    session.accept(jsonrpcResponse)
                            .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                            .block();
                    ctx.response().setStatusCode(202).end();
                } else if (message instanceof McpSchema.JSONRPCNotification jsonrpcNotification) {
                    session.accept(jsonrpcNotification)
                            .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                            .block();
                    ctx.response().setStatusCode(202).end();
                } else if (message instanceof McpSchema.JSONRPCRequest jsonrpcRequest) {
                    handleStreamingRequest(ctx, session, jsonrpcRequest, sessionId, transportContext);
                } else {
                    sendMcpError(ctx, 400, McpSchema.ErrorCodes.INVALID_REQUEST, "Unknown message type");
                }
            } catch (Exception e) {
                LOG.errorf(e, "Error handling POST message");
                sendMcpError(
                        ctx, 400, McpSchema.ErrorCodes.INVALID_REQUEST, "Invalid message format: " + e.getMessage());
            }
        }
    }

    private void handleInitialize(RoutingContext ctx, McpSchema.JSONRPCRequest jsonrpcRequest) {
        McpStreamableServerSession.McpStreamableServerSessionInit init = null;
        try {
            McpSchema.InitializeRequest initializeRequest =
                    jsonMapper.convertValue(jsonrpcRequest.params(), new TypeRef<McpSchema.InitializeRequest>() {});
            init = sessionFactory.startSession(initializeRequest);
            sessions.put(init.session().getId(), init.session());

            McpSchema.InitializeResult initResult = init.initResult().block();
            String jsonResponse =
                    jsonMapper.writeValueAsString(McpSchema.JSONRPCResponse.result(jsonrpcRequest.id(), initResult));

            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", APPLICATION_JSON)
                    .putHeader(HttpHeaders.MCP_SESSION_ID, init.session().getId())
                    .end(jsonResponse);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to initialize session");
            if (init != null) {
                sessions.remove(init.session().getId());
            }
            sendMcpError(ctx, 500, McpSchema.ErrorCodes.INTERNAL_ERROR, "Failed to initialize session");
        }
    }

    private void handleStreamingRequest(
            RoutingContext ctx,
            McpStreamableServerSession session,
            McpSchema.JSONRPCRequest jsonrpcRequest,
            String sessionId,
            McpTransportContext transportContext) {

        HttpServerResponse response = ctx.response();
        response.setChunked(true).putHeader("Content-Type", TEXT_EVENT_STREAM).putHeader("Cache-Control", "no-cache");

        VertxStreamableMcpSessionTransport sessionTransport =
                new VertxStreamableMcpSessionTransport(sessionId, response);

        try {
            session.responseStream(jsonrpcRequest, sessionTransport)
                    .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                    .block();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to handle request stream for session %s", sessionId);
            if (!response.ended()) {
                response.end();
            }
        }
    }

    private void handleGet(RoutingContext ctx) {
        if (isClosing) {
            sendError(ctx, 503, "Server is shutting down");
            return;
        }

        String accept = ctx.request().getHeader("Accept");
        if (accept == null || !accept.contains(TEXT_EVENT_STREAM)) {
            sendError(ctx, 400, "text/event-stream required in Accept header");
            return;
        }

        String sessionId = ctx.request().getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            sendError(ctx, 400, "Session ID required in mcp-session-id header");
            return;
        }

        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            sendError(ctx, 404, "Session not found");
            return;
        }

        HttpServerResponse response = ctx.response();
        response.setChunked(true).putHeader("Content-Type", TEXT_EVENT_STREAM).putHeader("Cache-Control", "no-cache");

        VertxStreamableMcpSessionTransport sessionTransport =
                new VertxStreamableMcpSessionTransport(sessionId, response);

        McpTransportContext transportContext = extractTransportContext(ctx.request());

        String lastEventId = ctx.request().getHeader("Last-Event-ID");
        if (lastEventId != null) {
            try {
                session.replay(lastEventId)
                        .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                        .toIterable()
                        .forEach(message -> {
                            try {
                                sessionTransport
                                        .sendMessage(message)
                                        .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                                        .block();
                            } catch (Exception e) {
                                LOG.errorf(e, "Failed to replay message");
                                if (!response.ended()) {
                                    response.end();
                                }
                            }
                        });
            } catch (Exception e) {
                LOG.errorf(e, "Failed to replay messages");
                if (!response.ended()) {
                    response.end();
                }
            }
        } else {
            McpStreamableServerSession.McpStreamableServerSessionStream listeningStream =
                    session.listeningStream(sessionTransport);

            response.closeHandler(v -> {
                LOG.debugf("SSE connection closed for session: %s", sessionId);
                listeningStream.close();
            });
        }
    }

    private void handleDelete(RoutingContext ctx) {
        if (isClosing) {
            sendError(ctx, 503, "Server is shutting down");
            return;
        }

        String sessionId = ctx.request().getHeader(HttpHeaders.MCP_SESSION_ID);
        if (sessionId == null || sessionId.isBlank()) {
            sendMcpError(
                    ctx, 400, McpSchema.ErrorCodes.INVALID_REQUEST, "Session ID required in mcp-session-id header");
            return;
        }

        McpStreamableServerSession session = sessions.get(sessionId);
        if (session == null) {
            sendError(ctx, 404, "Session not found");
            return;
        }

        McpTransportContext transportContext = extractTransportContext(ctx.request());

        try {
            session.delete()
                    .contextWrite(c -> c.put(McpTransportContext.KEY, transportContext))
                    .block();
            sessions.remove(sessionId);
            ctx.response().setStatusCode(200).end();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to delete session %s", sessionId);
            sendMcpError(ctx, 500, McpSchema.ErrorCodes.INTERNAL_ERROR, e.getMessage());
        }
    }

    public static final String HTTP_HEADERS_KEY = "wanaku.http.headers";

    private McpTransportContext extractTransportContext(HttpServerRequest request) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        Map<String, String> httpHeaders = new java.util.HashMap<>();
        request.headers().forEach(entry -> {
            metadata.put(entry.getKey(), entry.getValue());
            httpHeaders.put(entry.getKey(), entry.getValue());
        });
        metadata.put(HTTP_HEADERS_KEY, httpHeaders);
        return McpTransportContext.create(metadata);
    }

    private void sendError(RoutingContext ctx, int statusCode, String message) {
        if (!ctx.response().ended()) {
            ctx.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", APPLICATION_JSON)
                    .end("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
        }
    }

    private void sendMcpError(RoutingContext ctx, int statusCode, int errorCode, String message) {
        try {
            McpError mcpError = McpError.builder(errorCode).message(message).build();
            String jsonError = jsonMapper.writeValueAsString(mcpError);
            if (!ctx.response().ended()) {
                ctx.response()
                        .setStatusCode(statusCode)
                        .putHeader("Content-Type", APPLICATION_JSON)
                        .end(jsonError);
            }
        } catch (Exception e) {
            sendError(ctx, statusCode, message);
        }
    }

    private class VertxStreamableMcpSessionTransport implements McpStreamableServerTransport {
        private final String sessionId;
        private final HttpServerResponse response;
        private volatile boolean closed = false;
        private final ReentrantLock lock = new ReentrantLock();

        VertxStreamableMcpSessionTransport(String sessionId, HttpServerResponse response) {
            this.sessionId = sessionId;
            this.response = response;
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
            return sendMessage(message, null);
        }

        @Override
        public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message, String messageId) {
            return Mono.fromRunnable(() -> {
                if (closed || response.ended()) {
                    return;
                }

                lock.lock();
                try {
                    if (closed || response.ended()) {
                        return;
                    }

                    String jsonText = jsonMapper.writeValueAsString(message);
                    StringBuilder sb = new StringBuilder();
                    if (messageId != null) {
                        sb.append("id: ").append(messageId).append("\n");
                    } else {
                        sb.append("id: ").append(sessionId).append("\n");
                    }
                    sb.append("event: ").append(MESSAGE_EVENT_TYPE).append("\n");
                    sb.append("data: ").append(jsonText).append("\n\n");
                    response.write(Buffer.buffer(sb.toString()));
                } catch (Exception e) {
                    LOG.errorf(e, "Failed to send message to session %s", sessionId);
                    sessions.remove(sessionId);
                    if (!response.ended()) {
                        response.end();
                    }
                } finally {
                    lock.unlock();
                }
            });
        }

        @Override
        public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
            return jsonMapper.convertValue(data, typeRef);
        }

        @Override
        public Mono<Void> closeGracefully() {
            return Mono.fromRunnable(this::close);
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                if (!response.ended()) {
                    response.end();
                }
            } catch (Exception e) {
                LOG.debugf(e, "Error closing session transport %s", sessionId);
            } finally {
                lock.unlock();
            }
        }
    }
}

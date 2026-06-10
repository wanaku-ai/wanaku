package ai.wanaku.core.capabilities.common;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.trace.Span;

@ApplicationScoped
public class TracingServerInterceptor implements ServerInterceptor {

    private static final Logger LOG = Logger.getLogger(TracingServerInterceptor.class);

    static final String SPAN_ATTR_REQUEST_ID = "wanaku.mcp.request_id";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String requestId = headers.get(Metadata.Key.of("x-wanaku-request-id", Metadata.ASCII_STRING_MARSHALLER));

        if (requestId != null && !requestId.isEmpty()) {
            try {
                Span.current().setAttribute(SPAN_ATTR_REQUEST_ID, requestId);
            } catch (Exception e) {
                LOG.tracef(e, "Could not set span attribute");
            }
        }

        return next.startCall(call, headers);
    }
}

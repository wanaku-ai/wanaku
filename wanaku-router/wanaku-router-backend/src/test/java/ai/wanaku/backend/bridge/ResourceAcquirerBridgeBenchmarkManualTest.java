package ai.wanaku.backend.bridge;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceManager;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.types.ResourceReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.ResourceReply;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class ResourceAcquirerBridgeBenchmarkManualTest {

    private ResourceAcquirerBridge bridge;
    private ResourceManager.ResourceArguments arguments;
    private ResourceReference resourceReference;

    @Setup(Level.Trial)
    public void setup() {
        ServiceTarget serviceTarget = ServiceTarget.newEmptyTarget("test-service", "localhost", 9090, "file");

        ServiceResolver serviceResolver = mock(ServiceResolver.class);
        when(serviceResolver.resolve(any(), any())).thenReturn(serviceTarget);

        ResourceReply reply = ResourceReply.newBuilder()
                .addContent("benchmark resource content")
                .build();

        WanakuBridgeTransport transport = mock(WanakuBridgeTransport.class);
        when(transport.acquireResource(any(), any())).thenReturn(reply);

        bridge = new ResourceAcquirerBridge(serviceResolver, transport);

        McpConnection connection = mock(McpConnection.class);
        when(connection.id()).thenReturn("bench-conn-1");

        arguments = mock(ResourceManager.ResourceArguments.class);
        when(arguments.connection()).thenReturn(connection);
        when(arguments.requestUri()).thenReturn(new RequestUri("file:///tmp/test.txt"));

        resourceReference = new ResourceReference();
        resourceReference.setLocation("/tmp/test.txt");
        resourceReference.setType("file");
        resourceReference.setName("test-resource");
        resourceReference.setMimeType("text/plain");
    }

    @Benchmark
    public Object evalResourceBenchmark() {
        return bridge.eval(arguments, resourceReference);
    }

    @Test
    void runBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
                .include(ResourceAcquirerBridgeBenchmarkManualTest.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

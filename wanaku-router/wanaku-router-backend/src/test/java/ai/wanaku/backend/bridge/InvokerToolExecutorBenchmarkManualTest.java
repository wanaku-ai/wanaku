package ai.wanaku.backend.bridge;

import java.util.HashMap;
import java.util.Map;
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
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ToolManager;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.types.InputSchema;
import ai.wanaku.capabilities.sdk.api.types.Property;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;

import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
public class InvokerToolExecutorBenchmarkManualTest {

    private InvokerToolExecutor executor;
    private ToolManager.ToolArguments toolArguments;
    private ToolReference toolReference;

    @Setup(Level.Trial)
    public void setup() {
        ServiceTarget serviceTarget = ServiceTarget.newEmptyTarget("test-service", "localhost", 8080, "http");

        ServiceResolver serviceResolver = mock(ServiceResolver.class);
        when(serviceResolver.resolve(any(), any())).thenReturn(serviceTarget);

        ToolInvokeReply reply =
                ToolInvokeReply.newBuilder().addContent("benchmark result").build();

        WanakuBridgeTransport transport = mock(WanakuBridgeTransport.class);
        when(transport.invokeTool(any(), any())).thenReturn(reply);

        executor = new InvokerToolExecutor(serviceResolver, transport);

        McpConnection connection = mock(McpConnection.class);
        when(connection.id()).thenReturn("bench-conn-1");

        toolArguments = mock(ToolManager.ToolArguments.class);
        when(toolArguments.connection()).thenReturn(connection);
        Map<String, Object> args = new HashMap<>();
        args.put("param1", "value1");
        when(toolArguments.args()).thenReturn(args);

        Map<String, Property> properties = new HashMap<>();
        Property p = new Property();
        p.setType("string");
        p.setDescription("A test parameter");
        properties.put("param1", p);

        InputSchema schema = new InputSchema();
        schema.setType("object");
        schema.setProperties(properties);

        toolReference = new ToolReference();
        toolReference.setName("bench-tool");
        toolReference.setType("http");
        toolReference.setUri("https://example.com/api/test");
        toolReference.setInputSchema(schema);
    }

    @Benchmark
    public void executeToolBenchmark(Blackhole blackhole) {
        blackhole.consume(executor.execute(toolArguments, toolReference));
    }

    @Test
    void runBenchmark() throws Exception {
        Options opt = new OptionsBuilder()
                .include(InvokerToolExecutorBenchmarkManualTest.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}

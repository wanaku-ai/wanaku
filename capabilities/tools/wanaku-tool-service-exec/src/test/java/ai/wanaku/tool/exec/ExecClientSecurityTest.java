package ai.wanaku.tool.exec;

import java.util.List;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.common.ConfigResourceLoader;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecClientSecurityTest {

    @Test
    void doesNotInvokeExecutorWhenExecutableIsNotAllowlisted() {
        RecordingExecutor executor = new RecordingExecutor();
        ExecClient client = new ExecClient(new ExecCommandPolicy(List.of("/bin/allowed")), executor);

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("/bin/denied --version").build();
        ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

        assertThrows(SecurityException.class, () -> client.exchange(request, configResource));
        assertNull(executor.command);
    }

    @Test
    void passesAllowlistedCommandToExecutor() {
        RecordingExecutor executor = new RecordingExecutor();
        ExecClient client = new ExecClient(new ExecCommandPolicy(List.of("/bin/allowed")), executor);

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("/bin/allowed --version").build();
        ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

        client.exchange(request, configResource);

        assertArrayEquals(new String[] {"/bin/allowed", "--version"}, executor.command);
    }

    private static final class RecordingExecutor implements CommandExecutor {
        private String[] command;

        @Override
        public Object run(String[] command) {
            this.command = command;
            return List.of(command);
        }
    }
}

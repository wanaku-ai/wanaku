package ai.wanaku.backend.api.v1.toolsetrepos;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ai.wanaku.backend.core.persistence.api.DataStoreRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.capabilities.sdk.api.types.DataStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolsetReposBeanRedirectSecurityTest {

    @Mock
    DataStoreRepository dataStoreRepository;

    ToolsetReposBean bean = new ToolsetReposBean() {
        @Override
        void validateUrl(String url) throws WanakuException {
            String host = URI.create(url).getHost();
            if ("127.0.0.1".equals(host)) {
                return;
            }
            if ("169.254.169.254".equals(host)) {
                throw new WanakuException("URLs pointing to private or reserved network ranges are not allowed");
            }
            throw new WanakuException("Unexpected host during redirect test: " + host);
        }
    };

    @Test
    void browse_revalidatesRedirectTargetBeforeFollowingIt() throws Exception {
        setDataStoreRepository(bean, dataStoreRepository);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repo/index.properties", this::redirectToMetadataIp);
        server.start();

        try {
            int port = server.getAddress().getPort();
            DataStore repo = new DataStore();
            repo.setId("repo-1");
            repo.setName("redirect-repo");
            repo.setData("{\"url\":\"http://127.0.0.1:" + port + "/repo\",\"branch\":\"main\"}");
            repo.setLabels(new HashMap<>(Map.of(ToolsetReposBean.LABEL_TYPE_KEY, ToolsetReposBean.LABEL_TYPE_VALUE)));

            when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=toolset-repo"))
                    .thenReturn(List.of(repo));

            WanakuException ex = assertThrows(WanakuException.class, () -> bean.browse("redirect-repo"));
            assertTrue(ex.getMessage().contains("private or reserved"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchToolset_rejectsUnsafeToolsetNames() throws Exception {
        setDataStoreRepository(bean, dataStoreRepository);

        DataStore repo = new DataStore();
        repo.setId("repo-2");
        repo.setName("safe-repo");
        repo.setData("{\"url\":\"http://127.0.0.1:8080/repo\",\"branch\":\"main\"}");
        repo.setLabels(new HashMap<>(Map.of(ToolsetReposBean.LABEL_TYPE_KEY, ToolsetReposBean.LABEL_TYPE_VALUE)));

        when(dataStoreRepository.findAllFilterByLabelExpression("wanaku.type=toolset-repo"))
                .thenReturn(List.of(repo));

        WanakuException ex = assertThrows(WanakuException.class, () -> bean.fetchToolset("safe-repo", "../etc/passwd"));
        assertTrue(ex.getMessage().contains("invalid characters"));
    }

    private void redirectToMetadataIp(HttpExchange exchange) throws java.io.IOException {
        exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data/");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void setDataStoreRepository(ToolsetReposBean bean, DataStoreRepository dataStoreRepository)
            throws Exception {
        Field field = ToolsetReposBean.class.getDeclaredField("dataStoreRepository");
        field.setAccessible(true);
        field.set(bean, dataStoreRepository);
    }
}

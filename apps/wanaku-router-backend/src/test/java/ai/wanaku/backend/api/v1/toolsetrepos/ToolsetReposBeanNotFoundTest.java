package ai.wanaku.backend.api.v1.toolsetrepos;

import java.util.Collections;
import ai.wanaku.backend.core.persistence.api.DataStoreRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ToolsetReposBean} returns HTTP-404-friendly exceptions
 * when a toolset repository is not found, rather than a generic
 * {@link ai.wanaku.capabilities.sdk.api.exceptions.WanakuException} (which maps to HTTP 500).
 */
@ExtendWith(MockitoExtension.class)
class ToolsetReposBeanNotFoundTest {

    @Mock
    DataStoreRepository dataStoreRepository;

    private ToolsetReposBean bean;

    @BeforeEach
    void setUp() throws Exception {
        bean = new ToolsetReposBean();

        // Inject the mock DataStoreRepository into the private field
        var field = ToolsetReposBean.class.getDeclaredField("dataStoreRepository");
        field.setAccessible(true);
        field.set(bean, dataStoreRepository);
    }

    @Test
    void browseNonExistentRepoThrowsResourceNotFoundException() {
        when(dataStoreRepository.findAllFilterByLabelExpression(anyString())).thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () -> bean.browse("non-existent-repo"));
    }

    @Test
    void updateNonExistentRepoThrowsResourceNotFoundException() {
        when(dataStoreRepository.findAllFilterByLabelExpression(anyString())).thenReturn(Collections.emptyList());

        assertThrows(
                ResourceNotFoundException.class,
                () -> bean.update("non-existent-repo", "https://example.com", "desc", null, null));
    }

    @Test
    void fetchToolsetFromNonExistentRepoThrowsResourceNotFoundException() {
        when(dataStoreRepository.findAllFilterByLabelExpression(anyString())).thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () -> bean.fetchToolset("non-existent-repo", "my-toolset"));
    }
}

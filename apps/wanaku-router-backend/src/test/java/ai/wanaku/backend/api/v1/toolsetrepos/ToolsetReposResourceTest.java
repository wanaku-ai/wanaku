package ai.wanaku.backend.api.v1.toolsetrepos;

import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolsetReposResourceTest {

    @Mock
    ToolsetReposBean toolsetReposBean;

    @InjectMocks
    ToolsetReposResource resource;

    @Test
    void browseNonExistentRepoThrowsResourceNotFound() {
        when(toolsetReposBean.browse("non-existent-repo"))
                .thenThrow(new ResourceNotFoundException("Toolset repository not found: non-existent-repo"));

        assertThrows(ResourceNotFoundException.class, () -> resource.browse("non-existent-repo"));
    }

    @Test
    void fetchToolsetFromNonExistentRepoThrowsResourceNotFound() {
        when(toolsetReposBean.fetchToolset("non-existent-repo", "some-toolset"))
                .thenThrow(new ResourceNotFoundException("Toolset repository not found: non-existent-repo"));

        assertThrows(ResourceNotFoundException.class, () -> resource.fetchToolset("non-existent-repo", "some-toolset"));
    }
}

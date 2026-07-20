package ai.wanaku.backend.api.v1.forwards;

import java.util.Collections;
import ai.wanaku.backend.core.persistence.api.ForwardReferenceRepository;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForwardsBeanTest {

    @Mock
    ForwardReferenceRepository forwardReferenceRepository;

    @InjectMocks
    ForwardsBean forwardsBean;

    @Test
    void refreshNonExistentForwardThrowsResourceNotFoundException() {
        String name = "does-not-exist";
        when(forwardReferenceRepository.findByName(name)).thenReturn(Collections.emptyList());

        ForwardReference hint = new ForwardReference();
        hint.setName(name);

        assertThrows(ResourceNotFoundException.class, () -> forwardsBean.refresh(hint));
    }
}

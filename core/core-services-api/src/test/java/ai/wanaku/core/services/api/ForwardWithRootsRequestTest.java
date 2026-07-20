package ai.wanaku.core.services.api;

import java.util.List;
import ai.wanaku.capabilities.sdk.api.types.ForwardReference;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForwardWithRootsRequestTest {

    @Test
    void defaultConstructorCreatesEmptyRequest() {
        ForwardWithRootsRequest request = new ForwardWithRootsRequest();
        assertNull(request.getForward());
        assertNull(request.getRoots());
    }

    @Test
    void settersAndGettersWork() {
        ForwardWithRootsRequest request = new ForwardWithRootsRequest();

        ForwardReference reference = new ForwardReference();
        reference.setName("my-forward");
        reference.setAddress("http://localhost:8080");
        request.setForward(reference);

        List<ForwardWithRootsRequest.RootEntry> roots = List.of(
                new ForwardWithRootsRequest.RootEntry("file:///home/user", "home"),
                new ForwardWithRootsRequest.RootEntry("file:///data", null));
        request.setRoots(roots);

        assertEquals("my-forward", request.getForward().getName());
        assertEquals("http://localhost:8080", request.getForward().getAddress());
        assertEquals(2, request.getRoots().size());
        assertEquals("file:///home/user", request.getRoots().get(0).getUri());
        assertEquals("home", request.getRoots().get(0).getName());
        assertEquals("file:///data", request.getRoots().get(1).getUri());
        assertNull(request.getRoots().get(1).getName());
    }
}

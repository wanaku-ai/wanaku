package ai.wanaku.core.capabilities.provider;

import java.lang.reflect.Field;
import java.util.List;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.capabilities.sdk.api.exceptions.ResourceNotFoundException;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.v1.ResourceReply;
import ai.wanaku.core.exchange.v1.ResourceRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractResourceDelegateTest {

    @Mock
    private ResourceConsumer consumer;

    @Mock
    private RegistrationManager registrationManager;

    private String configuredUri;
    private List<String> configuredResponse;
    private RuntimeException coerceException;

    private AbstractResourceDelegate delegate;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        configuredUri = "test://resource";
        configuredResponse = null;
        coerceException = null;

        delegate = new AbstractResourceDelegate() {
            @Override
            protected String getEndpointUri(ResourceRequest request, ConfigResource configResource) {
                return configuredUri;
            }

            @Override
            protected List<String> coerceResponse(Object response)
                    throws InvalidResponseTypeException, NonConvertableResponseException, ResourceNotFoundException {
                if (coerceException instanceof InvalidResponseTypeException) {
                    throw (InvalidResponseTypeException) coerceException;
                }
                if (coerceException instanceof NonConvertableResponseException) {
                    throw (NonConvertableResponseException) coerceException;
                }
                if (coerceException instanceof ResourceNotFoundException) {
                    throw (ResourceNotFoundException) coerceException;
                }
                return configuredResponse;
            }
        };

        setField(delegate, "consumer", consumer);
        setField(delegate, "registrationManager", registrationManager);
    }

    @Test
    void acquireHappyPath() throws Exception {
        configuredResponse = List.of("resource content");
        when(consumer.consume(any(String.class), any(ResourceRequest.class))).thenReturn("raw");

        ResourceRequest request =
                ResourceRequest.newBuilder().setLocation("test-location").build();

        ResourceReply reply = delegate.acquire(request);

        assertNotNull(reply);
        assertEquals(1, reply.getContentCount());
        assertEquals("resource content", reply.getContent(0));
    }

    @Test
    void acquireVerifiesUriForwarding() throws Exception {
        configuredUri = "custom://my-endpoint";
        configuredResponse = List.of("data");
        when(consumer.consume(any(String.class), any(ResourceRequest.class))).thenReturn("raw");

        ResourceRequest request =
                ResourceRequest.newBuilder().setLocation("test-location").build();

        delegate.acquire(request);

        verify(consumer).consume(eq("custom://my-endpoint"), any(ResourceRequest.class));
    }

    @Test
    void acquireMultipleContentItems() throws Exception {
        configuredResponse = List.of("item1", "item2", "item3");
        when(consumer.consume(any(String.class), any(ResourceRequest.class))).thenReturn("raw");

        ResourceRequest request =
                ResourceRequest.newBuilder().setLocation("test-location").build();

        ResourceReply reply = delegate.acquire(request);

        assertEquals(3, reply.getContentCount());
        assertEquals("item1", reply.getContent(0));
        assertEquals("item2", reply.getContent(1));
        assertEquals("item3", reply.getContent(2));
    }

    @Test
    void acquireInvalidResponseType() throws Exception {
        coerceException = new InvalidResponseTypeException("bad type");
        when(consumer.consume(any(String.class), any(ResourceRequest.class))).thenReturn("raw");

        ResourceRequest request =
                ResourceRequest.newBuilder().setLocation("test-location").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> delegate.acquire(request));

        assertEquals(Status.INTERNAL.getCode(), ex.getStatus().getCode());
    }

    @Test
    void acquireNonConvertableResponse() throws Exception {
        coerceException = new NonConvertableResponseException("cannot convert");
        when(consumer.consume(any(String.class), any(ResourceRequest.class))).thenReturn("raw");

        ResourceRequest request =
                ResourceRequest.newBuilder().setLocation("test-location").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> delegate.acquire(request));

        assertEquals(Status.INTERNAL.getCode(), ex.getStatus().getCode());
    }

    @Test
    void acquireConsumerThrowsException() throws Exception {
        when(consumer.consume(any(String.class), any(ResourceRequest.class)))
                .thenThrow(new RuntimeException("connection failed"));

        ResourceRequest request =
                ResourceRequest.newBuilder().setLocation("test-location").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> delegate.acquire(request));

        assertEquals(Status.INTERNAL.getCode(), ex.getStatus().getCode());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}

package ai.wanaku.core.capabilities.tool;

import java.lang.reflect.Field;
import java.util.List;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import ai.wanaku.capabilities.sdk.api.discovery.RegistrationManager;
import ai.wanaku.capabilities.sdk.api.exceptions.InvalidResponseTypeException;
import ai.wanaku.capabilities.sdk.api.exceptions.NonConvertableResponseException;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.v1.ToolInvokeReply;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractToolDelegateTest {

    @Mock
    private Client client;

    @Mock
    private RegistrationManager registrationManager;

    private List<String> configuredResponse;
    private RuntimeException coerceException;

    private AbstractToolDelegate delegate;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        configuredResponse = null;
        coerceException = null;

        delegate = new AbstractToolDelegate() {
            @Override
            protected List<String> coerceResponse(Object response)
                    throws InvalidResponseTypeException, NonConvertableResponseException {
                if (coerceException instanceof InvalidResponseTypeException) {
                    throw (InvalidResponseTypeException) coerceException;
                }
                if (coerceException instanceof NonConvertableResponseException) {
                    throw (NonConvertableResponseException) coerceException;
                }
                return configuredResponse;
            }
        };

        setField(delegate, "client", client);
        setField(delegate, "registrationManager", registrationManager);
    }

    @Test
    void invokeHappyPath() throws Exception {
        configuredResponse = List.of("result data");
        when(client.exchange(any(ToolInvokeRequest.class), any(ConfigResource.class)))
                .thenReturn("raw");

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("test://tool").build();

        ToolInvokeReply reply = delegate.invoke(request);

        assertNotNull(reply);
        assertEquals(1, reply.getContentCount());
        assertEquals("result data", reply.getContent(0));
        verify(registrationManager).lastAsSuccessful();
    }

    @Test
    void invokeMultipleContentItems() throws Exception {
        configuredResponse = List.of("item1", "item2", "item3");
        when(client.exchange(any(ToolInvokeRequest.class), any(ConfigResource.class)))
                .thenReturn("raw");

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("test://tool").build();

        ToolInvokeReply reply = delegate.invoke(request);

        assertEquals(3, reply.getContentCount());
        assertEquals("item1", reply.getContent(0));
        assertEquals("item2", reply.getContent(1));
        assertEquals("item3", reply.getContent(2));
        verify(registrationManager).lastAsSuccessful();
    }

    @Test
    void invokeInvalidResponseType() throws Exception {
        coerceException = new InvalidResponseTypeException("bad type");
        when(client.exchange(any(ToolInvokeRequest.class), any(ConfigResource.class)))
                .thenReturn("raw");

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("test://tool").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> delegate.invoke(request));

        assertEquals(Status.INTERNAL.getCode(), ex.getStatus().getCode());
        verify(registrationManager).lastAsFail(any(String.class));
    }

    @Test
    void invokeNonConvertableResponse() throws Exception {
        coerceException = new NonConvertableResponseException("cannot convert");
        when(client.exchange(any(ToolInvokeRequest.class), any(ConfigResource.class)))
                .thenReturn("raw");

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("test://tool").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> delegate.invoke(request));

        assertEquals(Status.INTERNAL.getCode(), ex.getStatus().getCode());
        verify(registrationManager).lastAsFail(any(String.class));
    }

    @Test
    void invokeClientThrowsException() throws Exception {
        when(client.exchange(any(ToolInvokeRequest.class), any(ConfigResource.class)))
                .thenThrow(new RuntimeException("connection failed"));

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri("test://tool").build();

        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class, () -> delegate.invoke(request));

        assertEquals(Status.INTERNAL.getCode(), ex.getStatus().getCode());
        verify(registrationManager).lastAsFail(any(String.class));
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

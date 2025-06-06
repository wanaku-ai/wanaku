package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.api.types.providers.ServiceTarget;
import ai.wanaku.api.types.providers.ServiceType;
import org.infinispan.protostream.MessageMarshaller;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ServiceTargetMarshaller implements MessageMarshaller<ServiceTarget> {

    @Override
    public String getTypeName() {
        return ServiceTarget.class.getCanonicalName();
    }

    @Override
    public Class<? extends ServiceTarget> getJavaClass() {
        return ServiceTarget.class;
    }

    @Override
    public ServiceTarget readFrom(ProtoStreamReader reader) throws IOException {
        String id = reader.readString("id");
        String service = reader.readString("service");
        String host = reader.readString("host");
        int port = reader.readInt("port");
        ServiceType serviceType = reader.readEnum("serviceType", ServiceType.class);
        Map<String, String> configurations = reader.readMap("configurations", new HashMap<>(), String.class, String.class);

        ServiceTarget serviceTarget = new ServiceTarget(id, service, host, port, serviceType, configurations);
        return serviceTarget;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ServiceTarget serviceTarget) throws IOException {
        writer.writeString("id", serviceTarget.getId());
        writer.writeString("service", serviceTarget.getService());
        writer.writeString("host", serviceTarget.getHost());
        writer.writeInt("port", serviceTarget.getPort());
        writer.writeEnum("serviceType", serviceTarget.getServiceType());
        writer.writeMap("configurations", serviceTarget.getConfigurations(),String.class,String.class);
    }
}

package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import java.io.IOException;
import org.infinispan.protostream.MessageMarshaller;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;

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
        String serviceName = reader.readString("serviceName");
        String host = reader.readString("host");
        int port = reader.readInt("port");
        String serviceType = reader.readString("serviceType");
        String serviceSubType = reader.readString("serviceSubType");
        String languageName = reader.readString("languageName");
        String languageType = reader.readString("languageType");
        String languageSubType = reader.readString("languageSubType");

        ServiceTarget serviceTarget = new ServiceTarget(
                id, serviceName, host, port, serviceType, serviceSubType, languageName, languageType, languageSubType);
        return serviceTarget;
    }

    @Override
    public void writeTo(ProtoStreamWriter writer, ServiceTarget serviceTarget) throws IOException {
        writer.writeString("id", serviceTarget.getId());
        writer.writeString("serviceName", serviceTarget.getServiceName());
        writer.writeString("host", serviceTarget.getHost());
        writer.writeInt("port", serviceTarget.getPort());
        writer.writeString("serviceType", serviceTarget.getServiceType());
        writer.writeString("serviceSubType", serviceTarget.getServiceSubType());
        writer.writeString("languageName", serviceTarget.getLanguageName());
        writer.writeString("languageType", serviceTarget.getLanguageType());
        writer.writeString("languageSubType", serviceTarget.getLanguageSubType());
    }
}

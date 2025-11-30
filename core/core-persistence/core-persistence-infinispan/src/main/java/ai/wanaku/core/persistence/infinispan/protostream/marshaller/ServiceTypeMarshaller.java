package ai.wanaku.core.persistence.infinispan.protostream.marshaller;

import ai.wanaku.capabilities.sdk.api.types.providers.ServiceType;
import org.infinispan.protostream.EnumMarshaller;

public class ServiceTypeMarshaller implements EnumMarshaller<ServiceType> {

    @Override
    public Class<ServiceType> getJavaClass() {
        return ServiceType.class;
    }

    @Override
    public String getTypeName() {
        return ServiceType.class.getCanonicalName();
    }

    @Override
    public ServiceType decode(int enumValue) {
        return ServiceType.fromIntValue(enumValue);
    }

    @Override
    public int encode(ServiceType serviceType) {
        return serviceType.intValue();
    }
}

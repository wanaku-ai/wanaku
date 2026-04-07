package ${package};

#if ( $wanaku-capability-type != "camel")
import jakarta.enterprise.context.ApplicationScoped;
#end
import ai.wanaku.core.capabilities.provider.ResourceConsumer;
import ai.wanaku.core.exchange.v1.ResourceRequest;

#if ( $wanaku-capability-type != "camel")
@ApplicationScoped
#end
public class ${name}ResourceConsumer implements ResourceConsumer {

    @Override
    public Object consume(String uri, ResourceRequest request) {
        // TODO: Implement your resource consumption logic here
        return null;
    }
}

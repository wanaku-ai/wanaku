import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kamelet.KameletComponent;
import org.apache.camel.impl.DefaultCamelContext;

/** Verifies Camel's native HTTP Kamelet lookup against an isolated Barn instance. */
public final class RemoteKameletProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected catalog URL, Kamelet name, and response");
        }
        try (var context = new DefaultCamelContext()) {
            context.getComponent("kamelet", KameletComponent.class).setLocation(args[0]);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:remote-catalog").to("kamelet:" + args[1]);
                }
            });
            context.start();
            try (var producer = context.createProducerTemplate()) {
                Object response = producer.requestBody("direct:remote-catalog", "request");
                if (!args[2].equals(response)) {
                    throw new IllegalStateException("Unexpected remote Kamelet response: " + response);
                }
            }
        }
        System.out.println("PASS: native Camel remote Kamelet lookup");
    }
}

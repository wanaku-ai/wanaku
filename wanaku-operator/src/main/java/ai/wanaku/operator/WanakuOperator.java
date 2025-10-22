package ai.wanaku.operator;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import jakarta.inject.Inject;

public class WanakuOperator implements QuarkusApplication {
    @Inject
    Operator operator;

    public static void main(String... args) {
        Quarkus.run(WanakuOperator.class, args);
    }

    @Override
    public int run(String... args) {
        operator.start();
        Quarkus.waitForExit();
        return 0;
    }
}

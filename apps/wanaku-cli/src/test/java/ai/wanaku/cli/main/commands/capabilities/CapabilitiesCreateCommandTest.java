package ai.wanaku.cli.main.commands.capabilities;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilitiesCreateCommandTest {

    private static class TestCapabilitiesCreateTool extends CapabilitiesCreateTool {
        String buildCommand() {
            return buildCreateCommand("mvn -B archetype:generate", "ai.wanaku.tool", "wanaku-tool-service");
        }
    }

    private static class TestCapabilitiesCreateResources extends CapabilitiesCreateResources {
        String buildCommand() {
            return buildCreateCommand("mvn -B archetype:generate", "ai.wanaku.provider", "wanaku-provider");
        }
    }

    @Test
    void toolProjectsDefaultToCamel() {
        TestCapabilitiesCreateTool command = new TestCapabilitiesCreateTool();
        command.name = "Kafka";

        String builtCommand = command.buildCommand();

        assertTrue(hasToken(builtCommand, CapabilityTypes.WANAKU_CAPABILITY_TYPE_OPTION + CapabilityTypes.CAMEL));
    }

    @Test
    void resourceProvidersDefaultToQuarkus() {
        TestCapabilitiesCreateResources command = new TestCapabilitiesCreateResources();
        command.name = "S3";

        String builtCommand = command.buildCommand();

        assertTrue(hasToken(builtCommand, CapabilityTypes.WANAKU_CAPABILITY_TYPE_OPTION + CapabilityTypes.QUARKUS));
    }

    @Test
    void resourceProvidersCanStillOptIntoCamel() {
        TestCapabilitiesCreateResources command = new TestCapabilitiesCreateResources();
        command.name = "S3";
        command.type = CapabilityTypes.CAMEL;

        String builtCommand = command.buildCommand();

        assertTrue(hasToken(builtCommand, CapabilityTypes.WANAKU_CAPABILITY_TYPE_OPTION + CapabilityTypes.CAMEL));
    }

    private static boolean hasToken(String command, String token) {
        return Arrays.stream(command.split("\\s+")).anyMatch(token::equals);
    }
}

package ai.wanaku.cli.main.commands.capabilities;

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

        assertTrue(builtCommand.contains("-Dwanaku-capability-type=camel"));
    }

    @Test
    void resourceProvidersDefaultToQuarkus() {
        TestCapabilitiesCreateResources command = new TestCapabilitiesCreateResources();
        command.name = "S3";

        String builtCommand = command.buildCommand();

        assertTrue(builtCommand.contains("-Dwanaku-capability-type=quarkus"));
    }

    @Test
    void resourceProvidersCanStillOptIntoCamel() {
        TestCapabilitiesCreateResources command = new TestCapabilitiesCreateResources();
        command.name = "S3";
        command.type = "camel";

        String builtCommand = command.buildCommand();

        assertTrue(builtCommand.contains("-Dwanaku-capability-type=camel"));
    }
}

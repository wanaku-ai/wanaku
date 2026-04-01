package ai.wanaku.cli.main.commands.capabilities;

/**
 * Shared capability type literals and related command tokens.
 */
public final class CapabilityTypes {
    public static final String CAMEL = "camel";
    public static final String QUARKUS = "quarkus";
    public static final String WANAKU_CAPABILITY_TYPE_OPTION = "-Dwanaku-capability-type=";
    public static final String OPTION_DESCRIPTION = "The capability type (" + CAMEL + ", " + QUARKUS + ")";

    private CapabilityTypes() {}
}

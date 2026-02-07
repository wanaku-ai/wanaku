package ai.wanaku.core.util;

/**
 * Reserved argument names
 */
public final class ReservedArgumentNames {
    /**
     * Represents the body of the message
     */
    public static final String BODY = "wanaku_body";

    /**
     * Prefix for metadata arguments that should be converted to headers.
     * Arguments with this prefix (e.g., "wanaku_meta_contextId") will be
     * extracted, the prefix stripped, and passed as headers to the tool service.
     */
    public static final String METADATA_PREFIX = "wanaku_meta_";

    private ReservedArgumentNames() {}
}

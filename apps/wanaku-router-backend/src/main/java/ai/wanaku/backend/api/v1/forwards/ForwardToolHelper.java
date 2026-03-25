package ai.wanaku.backend.api.v1.forwards;

import java.util.Set;
import java.util.function.Predicate;
import ai.wanaku.capabilities.sdk.api.types.RemoteToolReference;

final class ForwardToolHelper {
    private ForwardToolHelper() {}

    /**
     * Builds a unique remote tool name, adding a forward prefix and numeric suffix when needed.
     */
    static String buildUniqueRemoteToolName(
            String remoteToolName, String forwardName, Predicate<String> alreadyExists, Set<String> reservedNames) {

        if (isNameAvailable(remoteToolName, alreadyExists, reservedNames)) {
            return remoteToolName;
        }

        String forwardPrefix = sanitizeToolNameComponent(forwardName);
        String base = forwardPrefix + "__" + remoteToolName;
        if (isNameAvailable(base, alreadyExists, reservedNames)) {
            return base;
        }

        for (int i = 2; i < Integer.MAX_VALUE; i++) {
            String candidate = base + "__" + i;
            if (isNameAvailable(candidate, alreadyExists, reservedNames)) {
                return candidate;
            }
        }

        // Should be unreachable
        throw new IllegalStateException("" + remoteToolName);
    }

    /**
     * Creates a copy of a {@link RemoteToolReference} for safe local mutation.
     */
    static RemoteToolReference copyRemoteToolReference(RemoteToolReference source) {
        RemoteToolReference copy = new RemoteToolReference();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setDescription(source.getDescription());
        copy.setType(source.getType());
        copy.setInputSchema(source.getInputSchema());
        return copy;
    }

    private static boolean isNameAvailable(
            String candidate, Predicate<String> alreadyExists, Set<String> reservedNames) {
        return !reservedNames.contains(candidate) && !alreadyExists.test(candidate);
    }

    private static String sanitizeToolNameComponent(String input) {
        if (input == null || input.isBlank()) {
            return "remote";
        }

        String trimmed = input.trim();
        StringBuilder out = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '_'
                    || c == '-'
                    || c == '.';
            out.append(ok ? c : '_');
        }

        String sanitized = out.toString();
        return sanitized.isBlank() ? "remote" : sanitized;
    }
}

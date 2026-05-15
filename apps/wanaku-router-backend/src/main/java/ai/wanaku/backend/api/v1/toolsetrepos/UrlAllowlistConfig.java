package ai.wanaku.backend.api.v1.toolsetrepos;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class UrlAllowlistConfig {

    static final String ALLOWLIST_CONFIG_KEY = "wanaku.toolset-repos.url-allowlist";

    @ConfigProperty(name = ALLOWLIST_CONFIG_KEY)
    Optional<String> rawAllowlistPatterns;

    private volatile AllowlistState state;

    private List<String> effectivePatterns() {
        return ensureState().effectivePatterns;
    }

    List<Pattern> compiledPatterns() {
        return ensureState().compiledPatterns;
    }

    boolean isAllowlistConfigured() {
        return !effectivePatterns().isEmpty();
    }

    boolean isHostAllowed(String host) {
        if (!isAllowlistConfigured()) {
            return true;
        }
        for (Pattern pattern : compiledPatterns()) {
            if (pattern.matcher(host).matches()) {
                return true;
            }
        }
        return false;
    }

    List<String> getAllowlistPatterns() {
        return Collections.unmodifiableList(effectivePatterns());
    }

    static Pattern toRegexPattern(String glob) {
        if (glob.equals("*")) {
            return Pattern.compile("^.*$", Pattern.CASE_INSENSITIVE);
        }
        StringBuilder regex = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        regex.append("(.*\\.)?");
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '.') {
                            i++;
                        }
                    } else {
                        regex.append("[^.]*");
                    }
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                case '?':
                    regex.append(".");
                    break;
                default:
                    if (Character.isLetterOrDigit(c) || c == '-') {
                        regex.append(c);
                    } else {
                        regex.append("\\").append(c);
                    }
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    void invalidateCache() {
        synchronized (this) {
            state = buildState();
        }
    }

    private AllowlistState ensureState() {
        AllowlistState current = state;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (state == null) {
                state = buildState();
            }
            return state;
        }
    }

    private AllowlistState buildState() {
        String raw = rawAllowlistPatterns.orElse("");
        List<String> patterns = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        List<Pattern> compiled =
                patterns.stream().map(UrlAllowlistConfig::toRegexPattern).collect(Collectors.toList());
        return new AllowlistState(patterns, compiled);
    }

    private static final class AllowlistState {
        private final List<String> effectivePatterns;
        private final List<Pattern> compiledPatterns;

        private AllowlistState(List<String> effectivePatterns, List<Pattern> compiledPatterns) {
            this.effectivePatterns = effectivePatterns;
            this.compiledPatterns = compiledPatterns;
        }
    }
}

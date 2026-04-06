package ai.wanaku.tool.exec;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Singleton
final class ExecCommandPolicy {
    private static final Set<Character> DISALLOWED_CHARACTERS = Set.of(';', '|', '&', '<', '>', '`', '$');

    private final List<String> allowedExecutables;

    @Inject
    ExecCommandPolicy(
            @ConfigProperty(name = "wanaku.service.exec.allowed-executables", defaultValue = "")
            String allowedExecutables) {
        this(parseAllowedExecutables(allowedExecutables));
    }

    ExecCommandPolicy(List<String> allowedExecutables) {
        this.allowedExecutables = allowedExecutables.stream()
                .map(ExecCommandPolicy::normalizeExecutable)
                .toList();
    }

    List<String> buildCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            throw new SecurityException("Command execution is denied because the configured URI is blank");
        }

        List<String> command = tokenize(rawCommand);
        if (command.isEmpty()) {
            throw new SecurityException("Command execution is denied because the configured URI is empty");
        }

        if (allowedExecutables.isEmpty()) {
            throw new SecurityException("Command execution is denied because no executables are allowlisted");
        }

        String executable = normalizeExecutable(command.get(0));
        if (!allowedExecutables.contains(executable)) {
            throw new SecurityException(
                    "Command execution is denied because the executable is not allowlisted: " + executable);
        }

        command.set(0, executable);
        return List.copyOf(command);
    }

    private static List<String> parseAllowedExecutables(String allowedExecutables) {
        if (allowedExecutables == null || allowedExecutables.isBlank()) {
            return List.of();
        }

        return Arrays.stream(allowedExecutables.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .toList();
    }

    private static String normalizeExecutable(String executable) {
        if (executable == null || executable.isBlank()) {
            throw new SecurityException("Command execution is denied because the executable path is blank");
        }

        return Path.of(executable.trim()).toAbsolutePath().normalize().toString();
    }

    private static List<String> tokenize(String rawCommand) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;

        for (int i = 0; i < rawCommand.length(); i++) {
            char currentChar = rawCommand.charAt(i);

            if (currentChar == '\n' || currentChar == '\r') {
                throw new SecurityException("Command execution is denied because the URI contains a newline");
            }

            if (DISALLOWED_CHARACTERS.contains(currentChar)) {
                throw new SecurityException(
                        "Command execution is denied because the URI contains a shell metacharacter: " + currentChar);
            }

            if (currentChar == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (currentChar == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (!inSingleQuotes && !inDoubleQuotes && Character.isWhitespace(currentChar)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(currentChar);
        }

        if (inSingleQuotes || inDoubleQuotes) {
            throw new SecurityException("Command execution is denied because the URI contains unbalanced quotes");
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}

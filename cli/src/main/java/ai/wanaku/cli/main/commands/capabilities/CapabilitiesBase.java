package ai.wanaku.cli.main.commands.capabilities;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.core.util.ProcessRunner;
import ai.wanaku.core.util.VersionHelper;
import java.io.File;
import org.jboss.logging.Logger;
import picocli.CommandLine;

public abstract class CapabilitiesBase extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(CapabilitiesBase.class);

    @CommandLine.Option(
            names = {"--name"},
            description = "A human-readable name for the capability",
            required = true,
            arity = "0..1")
    protected String name;

    @CommandLine.Option(
            names = {"--wanaku-version"},
            description = "Wanaku base version",
            arity = "0..1")
    protected String wanakuVersion;

    @CommandLine.Option(
            names = {"--path"},
            description = "The project path",
            defaultValue = ".",
            arity = "0..1")
    protected String path;

    @CommandLine.Option(
            names = {"--type"},
            description = "The capability type (camel, quarkus, etc)",
            defaultValue = "camel",
            required = true,
            arity = "0..1")
    protected String type;

    protected void createProject(String baseCmd, String basePackage, String baseArtifactId) {
        String version = wanakuVersion != null ? wanakuVersion : VersionHelper.VERSION;
        String packageName = String.format("%s.%s", basePackage, sanitizeName(name));
        String cmd = String.format(
                "%s -Dpackage=%s -DartifactId=%s-%s -Dname=%s -Dwanaku-version=%s -Dwanaku-capability-type=%s -DarchetypeVersion=%s",
                baseCmd, packageName, baseArtifactId, sanitizeName(name), capitalize(name), version, type, version);

        String[] split = cmd.split(" ");
        final File projectDir = new File(path);
        try {
            ProcessRunner.run(projectDir, split);
        } catch (WanakuException e) {
            LOG.error(e.getMessage(), e);
            System.exit(-1);
        }
    }

    private static String capitalize(String ret) {
        final char[] chars = ret.toCharArray();

        // OK here.
        chars[0] = Character.toUpperCase(chars[0]);
        return replaceInvalid(new String(chars));
    }

    private static String replaceInvalid(String name) {
        return name.replace("-", "");
    }

    private static String sanitizeName(String name) {
        return replaceInvalid(name.toLowerCase());
    }
}

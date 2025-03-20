package ai.wanaku.cli.main.commands.start;

import jakarta.inject.Inject;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.runner.local.LocalRunner;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.jboss.logging.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "local", description = "Create a new tool service")
public class StartLocal extends StartBase {
    private static final Logger LOG = Logger.getLogger(StartLocal.class);

    @Inject
    WanakuCliConfig config;

    @Override
    protected void startWanaku() {
        if (exclusive.services == null) {
            exclusive.services = config.defaultServices();
        }

        LocalRunner localRunner = new LocalRunner(config);
        try {
            localRunner.start(exclusive.services);
        } catch (WanakuException | IOException e) {
            LOG.errorf(e, e.getMessage());
        }
    }

    public static void deleteDirectory(File dir) {
        if (!dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (String child : Objects.requireNonNull(children)) {
                File childDir = new File(dir, child);

                deleteDirectory(childDir);
            }
        }

        if (!dir.delete()) {
            System.err.println("Failed to delete " + dir);
        }
    }

    @Override
    public void run() {
        if (exclusive.listServices) {
            Map<String, String> components = config.components();
            for (String component : components.keySet()) {
                System.out.println(" - " + component);
            }
            return;
        }

        if (exclusive.clean) {
            System.out.println("Removing Wanaku cache directory");
            deleteDirectory(new File(RuntimeConstants.WANAKU_CACHE_DIR));

            System.out.println("Removing Wanaku local instance directory");
            deleteDirectory(new File(RuntimeConstants.WANAKU_LOCAL_DIR));

            return;
        }

        startWanaku();
    }
}

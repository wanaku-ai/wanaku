package ai.wanaku.core.util;

import ai.wanaku.api.exceptions.WanakuException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import org.jboss.logging.Logger;

public class ProcessRunner {
    private static final Logger LOG = Logger.getLogger(ProcessRunner.class);

    private ProcessRunner() {}

    public static String runWithOutput(String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            // Redirect output and error streams to a pipe
            processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);

            final Process process = processBuilder.start();
            LOG.info("Waiting for process to finish...");

            String output = readOutput(process);

            waitForExit(process);
            return output;
        } catch (IOException e) {
            LOG.error("I/O Error: %s", e.getMessage(), e);
            throw new WanakuException(e);
        } catch (InterruptedException e) {
            LOG.error("Interrupted: %s", e.getMessage(), e);
            throw new WanakuException(e);
        }
    }

    private static String readOutput(Process process) throws IOException {
        // Read the output from the process
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

    public static void run(File directory, String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command);
            processBuilder.inheritIO();
            processBuilder.directory(directory);

            final Process process = processBuilder.start();

            LOG.info("Waiting for process to finish...");
            waitForExit(process);
        } catch (IOException e) {
            LOG.error("I/O Error: %s", e.getMessage(), e);
            throw new WanakuException(e);
        } catch (InterruptedException e) {
            LOG.error("Interrupted: %s", e.getMessage(), e);
            throw new WanakuException(e);
        }
    }

    private static void waitForExit(Process process) throws InterruptedException {
        Thread thread = new Thread(() -> {
            if (process.isAlive()) {
                process.destroy();
            }
        });

        try {
            Runtime.getRuntime().addShutdownHook(thread);

            final int ret = process.waitFor();
            if (ret != 0) {
                LOG.warnf("Process did not execute successfully: (return status %d)", ret);
            }
        } finally {
            if (!process.isAlive()) {
                Runtime.getRuntime().removeShutdownHook(thread);
            }
        }
    }
}

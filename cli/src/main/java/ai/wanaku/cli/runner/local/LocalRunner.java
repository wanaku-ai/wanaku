package ai.wanaku.cli.runner.local;

import ai.wanaku.cli.main.support.Downloader;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.ZipHelper;
import ai.wanaku.core.util.ProcessRunner;
import ai.wanaku.core.util.VersionHelper;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.logging.Logger;

public class LocalRunner {
    private static final Logger LOG = Logger.getLogger(LocalRunner.class);
    private final WanakuCliConfig config;
    private int activeServices = 0;

    public static class LocalRunnerEnvironment {
        private final Map<String, String> servicesOptions = new HashMap<>();


        public LocalRunnerEnvironment() {}

        public LocalRunnerEnvironment withServiceOption(String key, String value) {
            servicesOptions.put(key, value);
            return this;
        }

        public Map<String, String> serviceOptions() {
            return servicesOptions;
        }

        public String serviceOptionsArguments() {
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, String> entry : servicesOptions.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }

            return sb.toString();
        }
    }

    private final LocalRunnerEnvironment environment;

    public LocalRunner(WanakuCliConfig config, LocalRunnerEnvironment environment) {
        this.config = config;
        this.environment = environment;
    }

    public void start(List<String> services) throws IOException {
        Map<String, String> components = config.components();
        deploy(services, components);

        run(services, components);
    }

    private void run(List<String> services, Map<String, String> components) {
        ExecutorService executorService = Executors.newCachedThreadPool();

        CountDownLatch countDownLatch = new CountDownLatch(activeServices);
        int grpcPort = config.initialGrpcPort();

        startRouter(RuntimeConstants.WANAKU_ROUTER_BACKEND, executorService, countDownLatch);
        LOG.infof("Waiting %d seconds for the Wanaku Router Backend to start", config.routerStartWaitSecs());
        try {
            Thread.sleep(Duration.ofSeconds(config.routerStartWaitSecs()).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for Wanaku Router Backend to start ... Aborting");
            return;
        }

        for (Map.Entry<String, String> component : components.entrySet()) {
            if (isEnabled(services, component)) {
                startService(component, grpcPort, executorService, countDownLatch, environment);
                grpcPort++;
            }
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            LOG.infof("Interrupted while waiting for services to run");
        }
    }

    private static void startRouter(String component, ExecutorService executorService, CountDownLatch countDownLatch) {
        File componentDir = new File(RuntimeConstants.WANAKU_LOCAL_DIR, component);

        executorService.submit(() -> {
            ProcessRunner.run(componentDir, "java", "-jar", "quarkus-run.jar");
            countDownLatch.countDown();
        });
    }

    private static void startService(
            Map.Entry<String, String> component,
            int grpcPort,
            ExecutorService executorService,
            CountDownLatch countDownLatch, LocalRunnerEnvironment environment) {
        LOG.infof("Starting Wanaku Service %s on port %d", component.getKey(), grpcPort);
        File componentDir = new File(RuntimeConstants.WANAKU_LOCAL_DIR, component.getKey());

        String serviceOptions = String.format("-Dquarkus.grpc.server.port=%d %s", grpcPort, environment.serviceOptionsArguments());

        executorService.submit(() -> {
            ProcessRunner.run(componentDir, "java", serviceOptions, "-jar", "quarkus-run.jar");
            countDownLatch.countDown();
        });
    }

    private void deploy(List<String> services, Map<String, String> components) throws IOException {
        downloadService(RuntimeConstants.WANAKU_ROUTER_BACKEND, components.get(RuntimeConstants.WANAKU_ROUTER_BACKEND));

        for (Map.Entry<String, String> component : components.entrySet()) {
            if (isEnabled(services, component)) {
                activeServices++;
                downloadService(component.getKey(), component.getValue());
            }
        }
    }

    private void downloadService(String componentName, String urlFormat) throws IOException {
        String downloadUrl = getDownloadURL(urlFormat);

        File destinationDir = new File(RuntimeConstants.WANAKU_CACHE_DIR);

        LOG.infof("Downloading %s at %s", componentName, downloadUrl);
        File downloadedFile = Downloader.downloadFile(downloadUrl, destinationDir);

        LOG.infof("Unpacking %s at %s", componentName, destinationDir);
        ZipHelper.unzip(downloadedFile, RuntimeConstants.WANAKU_LOCAL_DIR, componentName);
    }

    private String getDownloadURL(String urlFormat) {
        String tag;
        if (VersionHelper.VERSION.contains("SNAPSHOT")) {
            tag = config.earlyAccessTag();
        } else {
            tag = String.format("v%s", VersionHelper.VERSION);
        }

        return String.format(urlFormat, tag, VersionHelper.VERSION);
    }

    private static boolean isEnabled(List<String> services, Map.Entry<String, String> component) {
        if (!services.isEmpty()) {
            return services.contains(component.getKey());
        }

        return true;
    }
}

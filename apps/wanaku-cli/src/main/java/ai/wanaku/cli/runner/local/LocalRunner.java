package ai.wanaku.cli.runner.local;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.common.ProcessRunner;
import ai.wanaku.cli.main.support.Downloader;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.ZipHelper;
import ai.wanaku.core.util.VersionHelper;

public class LocalRunner {
    private static final Logger LOG = Logger.getLogger(LocalRunner.class);
    private static final String QUARKUS_APP = "quarkus-app";
    private static final URI DEFAULT_ROUTER_READINESS_URI = URI.create("http://localhost:8080/q/health/ready");
    private static final Duration ROUTER_READINESS_POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration ROUTER_READINESS_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private final WanakuCliConfig config;
    private final HttpClient httpClient;
    private final URI routerReadinessUri;
    private int activeServices = 0;

    public static class LocalRunnerEnvironment {
        private final Map<String, String> servicesOptions = new HashMap<>();
        private final List<File> localDists = new ArrayList<>();

        public LocalRunnerEnvironment() {
            withAuthMode("none");
        }

        private LocalRunnerEnvironment withAuthMode(String authMode) {
            servicesOptions.put("WANAKU_HTTP_AUTH", authMode);
            return this;
        }

        public LocalRunnerEnvironment withLocalDist(File localDist) {
            this.localDists.add(localDist);
            return this;
        }

        public List<File> localDists() {
            return localDists;
        }

        public Map<String, String> serviceOptions() {
            return servicesOptions;
        }

        public String serviceOptionsArguments() {
            StringBuilder sb = new StringBuilder();

            for (Map.Entry<String, String> entry : servicesOptions.entrySet()) {
                if (entry.getValue() != null) {
                    sb.append(entry.getKey())
                            .append("=")
                            .append(entry.getValue())
                            .append(" ");
                }
            }

            return sb.toString();
        }
    }

    private final LocalRunnerEnvironment environment;

    public LocalRunner(WanakuCliConfig config, LocalRunnerEnvironment environment) {
        this(config, environment, HttpClient.newHttpClient(), DEFAULT_ROUTER_READINESS_URI);
    }

    LocalRunner(
            WanakuCliConfig config, LocalRunnerEnvironment environment, HttpClient httpClient, URI routerReadinessUri) {
        this.config = config;
        this.environment = environment;
        this.httpClient = httpClient;
        this.routerReadinessUri = routerReadinessUri;
    }

    public void start(List<String> services) throws IOException {
        Map<String, String> components = config.components();
        deploy(services, components);

        reaugment(services, components);

        run(services, components);
    }

    private void reaugment(List<String> services, Map<String, String> components) {
        LOG.info("Preparing components for local mode");

        reaugmentComponent(
                RuntimeConstants.WANAKU_ROUTER_BACKEND,
                "-Dquarkus.oidc.enabled=false",
                "-Dquarkus.oidc-proxy.enabled=false");

        for (Map.Entry<String, String> component : components.entrySet()) {
            if (isEnabled(services, component)) {
                reaugmentComponent(component.getKey(), "-Dquarkus.oidc-client.enabled=false");
            }
        }
    }

    private static void reaugmentComponent(String componentName, String... buildTimeProperties) {
        File componentDir = quarkusAppDir(componentName);

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-Dquarkus.launch.rebuild=true");
        command.add("-Dquarkus.log.level=WARNING");
        command.addAll(List.of(buildTimeProperties));
        command.add("-jar");
        command.add("quarkus-run.jar");

        LOG.debugf("Re-augmenting %s", componentName);
        ProcessRunner.run(componentDir, command.toArray(new String[0]));
    }

    private void run(List<String> services, Map<String, String> components) throws IOException {
        ExecutorService executorService = Executors.newCachedThreadPool();

        CountDownLatch countDownLatch = new CountDownLatch(activeServices + 1);
        int grpcPort = config.initialGrpcPort();

        String profileOpt = String.format("-Dquarkus.profile=%s", config.localProfile());

        LOG.debug("Starting Wanaku Router Backend");
        startRouter(RuntimeConstants.WANAKU_ROUTER_BACKEND, profileOpt, executorService, countDownLatch, environment);
        LOG.infof(
                "Waiting up to %d seconds for the Wanaku Router Backend to become ready", config.routerStartWaitSecs());
        try {
            waitForRouterReadiness(Duration.ofSeconds(config.routerStartWaitSecs()), ROUTER_READINESS_POLL_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("Interrupted while waiting for Wanaku Router Backend to start ... Aborting");
            return;
        }

        for (Map.Entry<String, String> component : components.entrySet()) {
            if (isEnabled(services, component)) {
                startService(component, grpcPort, profileOpt, executorService, countDownLatch, environment);
                grpcPort++;
            }
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            LOG.infof("Interrupted while waiting for services to run");
        }
    }

    void waitForRouterReadiness(Duration timeout, Duration pollInterval) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() <= deadline) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                break;
            }

            Duration remaining = Duration.ofNanos(remainingNanos);
            if (routerIsReady(minDuration(remaining, ROUTER_READINESS_REQUEST_TIMEOUT))) {
                LOG.info("Wanaku Router Backend is ready");
                return;
            }

            Thread.sleep(Math.min(pollInterval.toMillis(), remaining.toMillis()));
        }

        throw new IOException("Wanaku Router Backend did not become ready within %d seconds at %s"
                .formatted(timeout.toSeconds(), routerReadinessUri));
    }

    private boolean routerIsReady(Duration timeout) throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(routerReadinessUri)
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException e) {
            LOG.debugf("Wanaku Router Backend readiness check failed: %s", e.getMessage());
            return false;
        }
    }

    private static Duration minDuration(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static void startRouter(
            String component,
            String profileOpt,
            ExecutorService executorService,
            CountDownLatch countDownLatch,
            LocalRunnerEnvironment environment) {
        File componentDir = quarkusAppDir(component);

        executorService.submit(() -> {
            try {
                ProcessRunner.run(
                        componentDir, environment.serviceOptions(), "java", profileOpt, "-jar", "quarkus-run.jar");
            } catch (Exception e) {
                LOG.errorf("Failed to start Wanaku Router Service: %s", e.getMessage(), e);
            } finally {
                countDownLatch.countDown();
            }
        });
    }

    private static void startService(
            Map.Entry<String, String> component,
            int grpcPort,
            String profileOpt,
            ExecutorService executorService,
            CountDownLatch countDownLatch,
            LocalRunnerEnvironment environment) {
        LOG.infof("Starting Wanaku Service %s on port %d", component.getKey(), grpcPort);
        File componentDir = quarkusAppDir(component.getKey());

        String grpcPortOpt = String.format("-Dquarkus.grpc.server.port=%d", grpcPort);

        executorService.submit(() -> {
            try {
                ProcessRunner.run(
                        componentDir,
                        environment.serviceOptions(),
                        "java",
                        profileOpt,
                        grpcPortOpt,
                        "-jar",
                        "quarkus-run.jar");
            } catch (Exception e) {
                LOG.errorf("Failed to start Wanaku Service %s", component.getKey(), e);
            } finally {
                countDownLatch.countDown();
            }
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
        String baseName = extractArtifactBaseName(urlFormat);
        File localMatch = findLocalDist(baseName);

        File zipFile;
        if (localMatch != null) {
            LOG.debugf("Using local distribution %s for %s", localMatch, componentName);
            zipFile = localMatch;
        } else {
            String downloadUrl = getDownloadURL(urlFormat);
            File destinationDir = new File(RuntimeConstants.WANAKU_CACHE_DIR);

            LOG.infof("Downloading %s", componentName);
            LOG.debugf("Download URL: %s", downloadUrl);
            zipFile = Downloader.downloadFile(downloadUrl, destinationDir);
        }

        String extractDir = componentName + File.separator + QUARKUS_APP;
        LOG.infof("Deploying %s", componentName);
        ZipHelper.unzip(zipFile, RuntimeConstants.WANAKU_LOCAL_DIR, extractDir);
    }

    static String extractArtifactBaseName(String urlFormat) {
        String filename = urlFormat.substring(urlFormat.lastIndexOf('/') + 1);
        int placeholder = filename.indexOf("-%s");
        if (placeholder > 0) {
            return filename.substring(0, placeholder);
        }
        return filename.replace(".zip", "");
    }

    private File findLocalDist(String baseName) {
        for (File file : environment.localDists()) {
            if (file.getName().startsWith(baseName)) {
                return file;
            }
        }
        return null;
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

    private static File quarkusAppDir(String componentName) {
        return new File(new File(RuntimeConstants.WANAKU_LOCAL_DIR, componentName), QUARKUS_APP);
    }

    private static boolean isEnabled(List<String> services, Map.Entry<String, String> component) {
        if (!services.isEmpty()) {
            return services.contains(component.getKey());
        }

        return true;
    }
}

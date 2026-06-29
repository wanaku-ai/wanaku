package ai.wanaku.cli.runner.local;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import ai.wanaku.cli.main.support.HttpUtil;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.ZipHelper;
import ai.wanaku.core.util.VersionHelper;

public class LocalRunner {
    private static final Logger LOG = Logger.getLogger(LocalRunner.class);
    private static final String QUARKUS_APP = "quarkus-app";
    private static final String STANDALONE_JAR = "app.jar";
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
        private String camelRoutes;
        private String camelRules;
        private String serviceCatalog;
        private String serviceCatalogSystem;
        private boolean failFast;

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

        public LocalRunnerEnvironment withCamelRoutes(String camelRoutes) {
            this.camelRoutes = camelRoutes;
            return this;
        }

        public LocalRunnerEnvironment withCamelRules(String camelRules) {
            this.camelRules = camelRules;
            return this;
        }

        public String camelRoutes() {
            return camelRoutes;
        }

        public String camelRules() {
            return camelRules;
        }

        public LocalRunnerEnvironment withServiceCatalog(String serviceCatalog) {
            this.serviceCatalog = serviceCatalog;
            return this;
        }

        public LocalRunnerEnvironment withServiceCatalogSystem(String serviceCatalogSystem) {
            this.serviceCatalogSystem = serviceCatalogSystem;
            return this;
        }

        public LocalRunnerEnvironment withFailFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public String serviceCatalog() {
            return serviceCatalog;
        }

        public String serviceCatalogSystem() {
            return serviceCatalogSystem;
        }

        public boolean failFast() {
            return failFast;
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

    public LocalRunner(WanakuCliConfig config, LocalRunnerEnvironment environment, boolean insecure) {
        this(config, environment, HttpUtil.newHttpClient(insecure), DEFAULT_ROUTER_READINESS_URI);
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
                if (!isQuarkusComponent(component.getKey())) {
                    LOG.infof("Skipping re-augmentation for non-Quarkus component %s", component.getKey());
                    continue;
                }
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
                var dashboardUri = routerReadinessUri.resolve("/admin");
                LOG.infof("Open the Wanaku dashboard available at %s/", dashboardUri);
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

    private void startService(
            Map.Entry<String, String> component,
            int grpcPort,
            String profileOpt,
            ExecutorService executorService,
            CountDownLatch countDownLatch,
            LocalRunnerEnvironment environment) {
        LOG.infof("Starting Wanaku Service %s (gRPC port %d)", component.getKey(), grpcPort);

        if (isQuarkusComponent(component.getKey())) {
            startQuarkusService(component.getKey(), grpcPort, profileOpt, executorService, countDownLatch, environment);
        } else {
            startStandaloneService(component.getKey(), grpcPort, executorService, countDownLatch, environment);
        }
    }

    private static void startQuarkusService(
            String componentName,
            int grpcPort,
            String profileOpt,
            ExecutorService executorService,
            CountDownLatch countDownLatch,
            LocalRunnerEnvironment environment) {
        File componentDir = quarkusAppDir(componentName);
        String grpcPortOpt = String.format("-Dquarkus.grpc.server.port=%d", grpcPort);
        String httpPortOpt = String.format("-Dquarkus.http.port=%d", grpcPort);

        executorService.submit(() -> {
            try {
                ProcessRunner.run(
                        componentDir,
                        environment.serviceOptions(),
                        "java",
                        profileOpt,
                        grpcPortOpt,
                        httpPortOpt,
                        "-jar",
                        "quarkus-run.jar");
            } catch (Exception e) {
                LOG.errorf("Failed to start Wanaku Service %s", componentName, e);
            } finally {
                countDownLatch.countDown();
            }
        });
    }

    private static void startStandaloneService(
            String componentName,
            int grpcPort,
            ExecutorService executorService,
            CountDownLatch countDownLatch,
            LocalRunnerEnvironment environment) {
        File componentDir = new File(RuntimeConstants.wanakuLocalDir(), componentName);

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add(STANDALONE_JAR);
        command.add("--registration-announce-address");
        command.add("localhost");
        command.add("--grpc-port");
        command.add(String.valueOf(grpcPort));
        command.add("--name");
        command.add(componentName);

        if (environment.serviceCatalog() != null) {
            command.add("--service-catalog");
            command.add(environment.serviceCatalog());
            if (environment.serviceCatalogSystem() != null) {
                command.add("--service-catalog-system");
                command.add(environment.serviceCatalogSystem());
            }
        } else {
            if (environment.camelRoutes() != null) {
                command.add("--routes-ref");
                command.add(environment.camelRoutes());
            }

            if (environment.camelRules() != null) {
                command.add("--rules-ref");
                command.add(environment.camelRules());
            }
        }

        if (environment.failFast()) {
            command.add("--fail-fast");
        }

        executorService.submit(() -> {
            try {
                ProcessRunner.run(componentDir, environment.serviceOptions(), command.toArray(new String[0]));
            } catch (Exception e) {
                LOG.errorf("Failed to start Wanaku Service %s", componentName, e);
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

        File downloadedFile;
        if (localMatch != null) {
            LOG.debugf("Using local distribution %s for %s", localMatch, componentName);
            downloadedFile = localMatch;
        } else {
            String downloadUrl = getDownloadURL(urlFormat);
            File destinationDir = new File(RuntimeConstants.wanakuCacheDir());

            LOG.infof("Downloading %s", componentName);
            LOG.debugf("Download URL: %s", downloadUrl);
            downloadedFile = Downloader.downloadFile(downloadUrl, destinationDir);
        }

        if (isJarArtifact(downloadedFile.getName())) {
            Path componentDir = Path.of(RuntimeConstants.wanakuLocalDir(), componentName);
            Files.createDirectories(componentDir);
            Files.copy(
                    downloadedFile.toPath(), componentDir.resolve(STANDALONE_JAR), StandardCopyOption.REPLACE_EXISTING);
            LOG.infof("Installed %s as standalone JAR", componentName);
        } else {
            String extractDir = componentName + File.separator + QUARKUS_APP;
            LOG.infof("Deploying %s", componentName);
            ZipHelper.unzip(downloadedFile, RuntimeConstants.wanakuLocalDir(), extractDir);
        }
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

    private static boolean isJarArtifact(String fileName) {
        return fileName.endsWith(".jar");
    }

    private static boolean isQuarkusComponent(String componentName) {
        File quarkusRunJar = new File(quarkusAppDir(componentName), "quarkus-run.jar");
        return quarkusRunJar.exists();
    }

    private static File quarkusAppDir(String componentName) {
        return new File(new File(RuntimeConstants.wanakuLocalDir(), componentName), QUARKUS_APP);
    }

    private static boolean isEnabled(List<String> services, Map.Entry<String, String> component) {
        if (!services.isEmpty()) {
            return services.contains(component.getKey());
        }

        return true;
    }
}

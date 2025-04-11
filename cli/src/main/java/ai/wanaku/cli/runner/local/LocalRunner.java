package ai.wanaku.cli.runner.local;

import ai.wanaku.cli.main.support.Downloader;
import ai.wanaku.cli.main.support.RuntimeConstants;
import ai.wanaku.cli.main.support.WanakuCliConfig;
import ai.wanaku.cli.main.support.ZipHelper;
import ai.wanaku.core.util.ProcessRunner;
import ai.wanaku.core.util.VersionHelper;
import java.io.File;
import java.io.IOException;
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

    public LocalRunner(WanakuCliConfig config) {
        this.config = config;
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

        for (Map.Entry<String, String> component : components.entrySet()) {
            if (isEnabled(services, component)) {
                File componentDir = new File(RuntimeConstants.WANAKU_LOCAL_DIR, component.getKey());

                String grpcPortArg = String.format("-Dquarkus.grpc.server.port=%d", grpcPort);
                executorService.submit(() -> {
                    ProcessRunner.run(componentDir, "java", grpcPortArg, "-jar", "quarkus-run.jar");
                    countDownLatch.countDown();
                });
                grpcPort++;
            }
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            LOG.infof("Interrupted while waiting for services to run");
        }
    }

    private void deploy(List<String> services, Map<String, String> components) throws IOException {
        for (Map.Entry<String, String> component : components.entrySet()) {
            if (isEnabled(services, component)) {
                activeServices++;
                String downloadUrl = getDownloadURL(component);
                String componentName = component.getKey();

                File destinationDir = new File(RuntimeConstants.WANAKU_CACHE_DIR);

                LOG.infof("Downloading %s at %s", componentName, downloadUrl);
                File downloadedFile = Downloader.downloadFile(downloadUrl, destinationDir);

                LOG.infof("Unpacking %s at %s", componentName, destinationDir);
                ZipHelper.unzip(downloadedFile, RuntimeConstants.WANAKU_LOCAL_DIR, componentName);
            }
        }
    }

    private String getDownloadURL(Map.Entry<String, String> component) {
        String tag;
        if (VersionHelper.VERSION.contains("SNAPSHOT")) {
            tag = config.earlyAccessTag();
        } else {
            tag =  String.format("v%s", VersionHelper.VERSION);
        }

        return String.format(component.getValue(), tag, VersionHelper.VERSION);
    }

    private static boolean isEnabled(List<String> services, Map.Entry<String, String> component) {
        if (component.getKey().equals("wanaku-router")) {
            return true;
        }

        if (!services.isEmpty()) {
            return services.contains(component.getKey());
        }

        return true;
    }

}

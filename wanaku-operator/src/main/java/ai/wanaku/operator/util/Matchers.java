package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.openshift.api.model.Route;
import java.util.Objects;

/**
 * Matching utilities to check if the desired resource matches the existing resource
 */
public final class Matchers {

    private Matchers() {}

    public static boolean match(Job desired, Job existing) {
        if (existing == null) {
            return false;
        } else {
            return desired.getSpec()
                            .getTemplate()
                            .getMetadata()
                            .getName()
                            .equals(existing.getSpec()
                                    .getTemplate()
                                    .getMetadata()
                                    .getName())
                    && desired.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .get(0)
                            .getImage()
                            .equals(existing.getSpec()
                                    .getTemplate()
                                    .getSpec()
                                    .getContainers()
                                    .get(0)
                                    .getImage());
        }
    }

    public static boolean match(Deployment desired, Deployment existing) {
        if (existing == null) {
            return false;
        } else {
            return desired.getSpec().getReplicas().equals(existing.getSpec().getReplicas())
                    && desired.getSpec()
                            .getTemplate()
                            .getSpec()
                            .getContainers()
                            .get(0)
                            .getImage()
                            .equals(existing.getSpec()
                                    .getTemplate()
                                    .getSpec()
                                    .getContainers()
                                    .get(0)
                                    .getImage());
        }
    }

    public static boolean match(Service desired, Service existing) {
        if (existing == null) {
            return false;
        }

        return desired.getMetadata().getName().equals(existing.getMetadata().getName());
    }

    public static boolean match(Route desiredRoute, Route existingRoute) {
        if (existingRoute == null) {
            return false;
        }

        return desiredRoute.getFullResourceName().equals(existingRoute.getFullResourceName());
    }

    public static boolean match(Ingress desired, Ingress existing) {
        if (existing == null) {
            return false;
        }

        // Compare name
        if (!desired.getMetadata().getName().equals(existing.getMetadata().getName())) {
            return false;
        }

        // Compare host from first rule
        String desiredHost = desired.getSpec().getRules().getFirst().getHost();
        String existingHost = existing.getSpec().getRules().getFirst().getHost();
        if (!Objects.equals(desiredHost, existingHost)) {
            return false;
        }

        // Compare backend service name from first rule's first path
        String desiredBackend = desired.getSpec()
                .getRules()
                .getFirst()
                .getHttp()
                .getPaths()
                .getFirst()
                .getBackend()
                .getService()
                .getName();
        String existingBackend = existing.getSpec()
                .getRules()
                .getFirst()
                .getHttp()
                .getPaths()
                .getFirst()
                .getBackend()
                .getService()
                .getName();

        return Objects.equals(desiredBackend, existingBackend);
    }

    public static boolean match(PersistentVolumeClaim desired, PersistentVolumeClaim existing) {
        if (existing == null) {
            return false;
        }

        // Check if storage request matches
        String desiredStorage =
                desired.getSpec().getResources().getRequests().get("storage").toString();
        String existingStorage =
                existing.getSpec().getResources().getRequests().get("storage").toString();

        return desiredStorage.equals(existingStorage)
                && desired.getSpec().getAccessModes().equals(existing.getSpec().getAccessModes());
    }
}

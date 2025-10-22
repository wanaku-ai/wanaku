package ai.wanaku.operator.util;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.openshift.api.model.Route;

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

        final ServiceSpec existingSpec = existing.getSpec();
        final ServiceSpec desiredSpec = desired.getSpec();

        return existingSpec.getExternalName().equals(desiredSpec.getExternalName())
                && existingSpec.getPorts().equals(desiredSpec.getPorts());
    }

    public static boolean match(Route desiredRoute, Route existingRoute) {
        if (existingRoute == null) {
            return false;
        }

        return desiredRoute.getFullResourceName().equals(existingRoute.getFullResourceName());
    }
}

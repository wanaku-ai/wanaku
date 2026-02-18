package ai.wanaku.backend.bridge;

import java.net.URI;
import org.jboss.logging.Logger;
import io.grpc.ManagedChannel;
import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.Configuration;
import ai.wanaku.core.exchange.v1.ProvisionReply;
import ai.wanaku.core.exchange.v1.ProvisionRequest;
import ai.wanaku.core.exchange.v1.ProvisionerGrpc;
import ai.wanaku.core.exchange.v1.Secret;

/**
 * Service for handling provisioning operations across all proxy implementations.
 * <p>
 * This class encapsulates the logic for provisioning configurations and secrets
 * to remote services via gRPC. It provides a consistent interface for all proxy
 * types, ensuring uniform error handling and response processing.
 * <p>
 * The provisioning process involves:
 * <ol>
 *   <li>Creating a provision request with configuration and secrets</li>
 *   <li>Sending the request to the remote service via gRPC</li>
 *   <li>Processing the response and creating a provisioning reference</li>
 *   <li>Handling any errors that occur during the process</li>
 * </ol>
 */
public class ProvisioningService {
    private static final Logger LOG = Logger.getLogger(ProvisioningService.class);

    /**
     * Provisions configuration and secrets to a remote service.
     * <p>
     * This method sends a provisioning request to the specified service and
     * returns a reference containing the URIs for accessing the provisioned
     * configuration and secrets, along with any additional properties.
     *
     * @param cfg the configuration to provision
     * @param secret the secrets to provision
     * @param channel the gRPC channel to use for communication
     * @param service the target service information
     * @return a provisioning reference with URIs and properties
     * @throws ServiceUnavailableException if the service cannot be reached or returns an error
     */
    public ProvisioningReference provision(
            Configuration cfg, Secret secret, ManagedChannel channel, ServiceTarget service) {

        LOG.debugf("Provisioning configuration '%s' to service: %s", cfg.getName(), service.toAddress());

        ProvisionRequest request = ProvisionRequest.newBuilder()
                .setConfiguration(cfg)
                .setSecret(secret)
                .build();

        ProvisionerGrpc.ProvisionerBlockingStub stub = ProvisionerGrpc.newBlockingStub(channel);

        try {
            ProvisionReply reply = stub.provision(request);

            LOG.debugf(
                    "Successfully provisioned configuration '%s' (config URI: %s, secret URI: %s)",
                    cfg.getName(), reply.getConfigurationUri(), reply.getSecretUri());

            return new ProvisioningReference(
                    URI.create(reply.getConfigurationUri()),
                    URI.create(reply.getSecretUri()),
                    reply.getPropertiesMap());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to provision configuration '%s' to service: %s", cfg.getName(), service.toAddress());
            throw ServiceUnavailableException.forAddress(service.toAddress());
        }
    }
}

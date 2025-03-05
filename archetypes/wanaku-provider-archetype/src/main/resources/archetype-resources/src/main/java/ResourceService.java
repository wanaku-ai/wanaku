/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.provider;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InquireRequest;
import ai.wanaku.core.exchange.Inquirer;
import ai.wanaku.core.exchange.ResourceAcquirer;
import ai.wanaku.core.exchange.ResourceAcquirerDelegate;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.service.discovery.util.DiscoveryUtil;
import ai.wanaku.core.services.config.WanakuProviderConfig;
import io.quarkus.grpc.GrpcService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@GrpcService
public class ResourceService implements ResourceAcquirer, Inquirer {
    private static final Logger LOG = Logger.getLogger(ResourceService.class);

    @Inject
    ResourceAcquirerDelegate delegate;

    @Inject
    WanakuProviderConfig config;

    @ConfigProperty(name = "quarkus.grpc.server.port")
    int port;

    @Override
    @Blocking
    public Uni<ResourceReply> resourceAcquire(ResourceRequest request) {
        return Uni.createFrom().item(() -> delegate.acquire(request));
    }

    @Override
    public Uni<InquireReply> inquire(InquireRequest request) {
        InquireReply reply = InquireReply.newBuilder()
                .putAllServiceConfigurations(delegate.serviceConfigurations())
                .putAllCredentialsConfigurations(delegate.credentialsConfigurations())
                .build();

        return Uni.createFrom().item(() -> reply);
    }

    void register(@Observes StartupEvent ev) {
        LOG.info("Registering resource service");

        delegate.register(config.name(), DiscoveryUtil.resolveRegistrationAddress(), port);
    }

    void deregister(@Observes ShutdownEvent ev) {
        LOG.info("De-registering resource service");

        delegate.deregister(config.name(), DiscoveryUtil.resolveRegistrationAddress(), port);
    }
}

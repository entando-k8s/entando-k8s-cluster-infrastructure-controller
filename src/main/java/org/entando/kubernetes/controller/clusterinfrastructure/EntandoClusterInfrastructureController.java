/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.clusterinfrastructure;

import static org.entando.kubernetes.controller.clusterinfrastructure.EntandoK8SServiceDeployableContainer.clientIdOf;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.StartupEvent;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.entando.kubernetes.controller.spi.common.ResourceUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.result.ExposedDeploymentResult;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.command.IngressingDeployCommand;
import org.entando.kubernetes.controller.support.controller.AbstractDbAwareController;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureSpec;

public class EntandoClusterInfrastructureController extends
        AbstractDbAwareController<EntandoClusterInfrastructureSpec, EntandoClusterInfrastructure> {

    @Inject
    public EntandoClusterInfrastructureController(KubernetesClient kubernetesClient) {
        super(kubernetesClient);

    }

    public EntandoClusterInfrastructureController(KubernetesClient kubernetesClient, boolean exitAutomatically) {
        super(kubernetesClient, exitAutomatically);

    }

    public EntandoClusterInfrastructureController(SimpleK8SClient<?> k8sClient, SimpleKeycloakClient keycloakClient) {
        super(k8sClient, keycloakClient);
    }

    public void onStartup(@Observes StartupEvent event) {
        processCommand();

    }

    @Override
    protected void synchronizeDeploymentState(EntandoClusterInfrastructure entandoClusterInfrastructure) {
        KeycloakConnectionConfig keycloakConnectionConfig = k8sClient.entandoResources()
                .findKeycloak(entandoClusterInfrastructure, entandoClusterInfrastructure.getSpec()::getKeycloakToUse);
        ClusterInfrastructureDeploymentResult entandoK8SService = deployEntandoK8SService(entandoClusterInfrastructure,
                keycloakConnectionConfig);
        saveClusterInfrastructureConnectionConfig(entandoClusterInfrastructure, entandoK8SService);
        if (entandoClusterInfrastructure.getSpec().isDefault()) {
            k8sClient.entandoResources().loadDefaultConfigMap()
                    .addToData(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY,
                            entandoClusterInfrastructure.getMetadata().getNamespace())
                    .addToData(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY,
                            entandoClusterInfrastructure.getMetadata().getName())
                    .done();
        }
    }

    private void saveClusterInfrastructureConnectionConfig(EntandoClusterInfrastructure entandoClusterInfrastructure,
            ExposedDeploymentResult<?> entandoK8SService) {
        k8sClient.secrets().createConfigMapIfAbsent(entandoClusterInfrastructure, new ConfigMapBuilder()
                .withNewMetadata()
                .withName(InfrastructureConfig.connectionConfigMapNameFor(entandoClusterInfrastructure))
                .addToOwnerReferences(ResourceUtils.buildOwnerReference(entandoClusterInfrastructure))
                .endMetadata()
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY, clientIdOf(entandoClusterInfrastructure))
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY, entandoK8SService.getInternalBaseUrl())
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY, entandoK8SService.getExternalBaseUrl())
                .build());
    }

    private ClusterInfrastructureDeploymentResult deployEntandoK8SService(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        EntandoK8SServiceDeployable deployable = new EntandoK8SServiceDeployable(entandoClusterInfrastructure, keycloakConnectionConfig);
        IngressingDeployCommand<ClusterInfrastructureDeploymentResult> command =
                new IngressingDeployCommand<>(deployable);
        ClusterInfrastructureDeploymentResult result = command.execute(k8sClient, keycloakClient);
        k8sClient.entandoResources().updateStatus(entandoClusterInfrastructure, command.getStatus());
        return result;
    }

}

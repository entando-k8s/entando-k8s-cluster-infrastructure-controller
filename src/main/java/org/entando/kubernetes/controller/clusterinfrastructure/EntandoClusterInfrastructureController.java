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

import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.StartupEvent;
import java.util.Optional;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import org.entando.kubernetes.controller.AbstractDbAwareController;
import org.entando.kubernetes.controller.DeployCommand;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.ExposedDeploymentResult;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.k8sclient.SimpleK8SClient;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public class EntandoClusterInfrastructureController extends AbstractDbAwareController<EntandoClusterInfrastructure> {

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
        KeycloakConnectionConfig keycloakConnectionConfig = k8sClient.entandoResources().findKeycloak(entandoClusterInfrastructure);

        ClusterInfrastructureDeploymentResult entandoK8SService = deployEntandoK8SService(entandoClusterInfrastructure,
                keycloakConnectionConfig);
        overwriteClusterInfrastructureSecret(entandoClusterInfrastructure, entandoK8SService,
                entandoClusterInfrastructure.getMetadata().getName() + "-connection-secret");
        if (entandoClusterInfrastructure.getSpec().isDefault()) {
            overwriteClusterInfrastructureSecret(entandoClusterInfrastructure, entandoK8SService,
                    EntandoOperatorConfig.getEntandoInfrastructureSecretName());
        }
    }

    private void overwriteClusterInfrastructureSecret(EntandoClusterInfrastructure entandoClusterInfrastructure,
            ExposedDeploymentResult<?> entandoK8SService, String secretName) {
        k8sClient.secrets().overwriteControllerSecret(new SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .endMetadata()
                .addToStringData("entandoK8SServiceClientId", clientIdOf(entandoClusterInfrastructure))
                .addToStringData("entandoK8SServiceInternalUrl", entandoK8SService.getInternalBaseUrl())
                .addToStringData("entandoK8SServiceExternalUrl", entandoK8SService.getExternalBaseUrl())
                .build());
    }

    private ClusterInfrastructureDeploymentResult deployEntandoK8SService(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        EntandoK8SServiceDeployable deployable = new EntandoK8SServiceDeployable(entandoClusterInfrastructure, keycloakConnectionConfig);
        DeployCommand<ClusterInfrastructureDeploymentResult, EntandoClusterInfrastructure> command = new DeployCommand<>(deployable);
        ClusterInfrastructureDeploymentResult result = command.execute(k8sClient, Optional.of(keycloakClient));
        k8sClient.entandoResources().updateStatus(entandoClusterInfrastructure, command.getStatus());
        return result;
    }

}

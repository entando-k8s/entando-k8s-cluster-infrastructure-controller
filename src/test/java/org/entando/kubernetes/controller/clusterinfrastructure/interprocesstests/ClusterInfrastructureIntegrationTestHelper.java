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

package org.entando.kubernetes.controller.clusterinfrastructure.interprocesstests;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.time.Duration;
import org.entando.kubernetes.controller.integrationtest.podwaiters.ServicePodWaiter;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.integrationtest.support.IntegrationTestHelperBase;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.model.EntandoCustomResourceStatus;
import org.entando.kubernetes.model.EntandoDeploymentPhase;
import org.entando.kubernetes.model.ResourceReference;
import org.entando.kubernetes.model.infrastructure.DoneableEntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureList;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureOperationFactory;

public class ClusterInfrastructureIntegrationTestHelper extends IntegrationTestHelperBase<
        EntandoClusterInfrastructure,
        EntandoClusterInfrastructureList,
        DoneableEntandoClusterInfrastructure
        > {

    public static final String CLUSTER_INFRASTRUCTURE_NAMESPACE = EntandoOperatorTestConfig
            .calculateNameSpace("entando-infra-namespace");
    public static final String CLUSTER_INFRASTRUCTURE_NAME = EntandoOperatorTestConfig.calculateName("eti");

    ClusterInfrastructureIntegrationTestHelper(DefaultKubernetesClient client) {
        super(client, EntandoClusterInfrastructureOperationFactory::produceAllEntandoClusterInfrastructures);
    }

    public void ensureInfrastructureConnectionConfig() {
        loadDefaultOperatorConfigMap()
                .addToData(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY, CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .addToData(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY, CLUSTER_INFRASTRUCTURE_NAME)
                .done();
        ResourceReference infrastructureToUse = new ResourceReference(CLUSTER_INFRASTRUCTURE_NAMESPACE, CLUSTER_INFRASTRUCTURE_NAME);
        delete(client.configMaps())
                .named(InfrastructureConfig.connectionConfigMapNameFor(infrastructureToUse))
                .fromNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .waitingAtMost(20, SECONDS);
        String hostName = "http://" + CLUSTER_INFRASTRUCTURE_NAME + "." + getDomainSuffix();
        client.configMaps()
                .inNamespace(CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .createNew()
                .withNewMetadata()
                .withName(InfrastructureConfig.connectionConfigMapNameFor(infrastructureToUse))
                .endMetadata()
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY, CLUSTER_INFRASTRUCTURE_NAME + "-k8s-svc")
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY, hostName + "/k8s")
                .addToData(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY, hostName + "/k8s")
                .done();
    }

    public void waitForClusterInfrastructure(EntandoClusterInfrastructure infrastructure, int waitOffset, boolean deployingDbContainers) {
        getOperations().inNamespace(infrastructure.getMetadata().getNamespace()).create(infrastructure);
        this.waitForServicePod(new ServicePodWaiter().limitReadinessTo(Duration.ofSeconds(150 + waitOffset)),
                infrastructure.getMetadata().getNamespace(), infrastructure.getMetadata().getName() + "-k8s-svc");
        await().atMost(30, SECONDS).until(
                () -> {
                    EntandoCustomResourceStatus status = getOperations()
                            .inNamespace(infrastructure.getMetadata().getNamespace())
                            .withName(infrastructure.getMetadata().getName())
                            .fromServer().get().getStatus();
                    return status.forServerQualifiedBy("k8s-svc").isPresent()
                            && status.getEntandoDeploymentPhase() == EntandoDeploymentPhase.SUCCESSFUL;
                });
    }

}

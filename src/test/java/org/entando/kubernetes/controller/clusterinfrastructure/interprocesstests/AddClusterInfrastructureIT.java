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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.client.EntandoOperatorTestConfig;
import org.entando.kubernetes.client.EntandoOperatorTestConfig.TestTarget;
import org.entando.kubernetes.client.integrationtesthelpers.FluentIntegrationTesting;
import org.entando.kubernetes.client.integrationtesthelpers.HttpTestHelper;
import org.entando.kubernetes.controller.clusterinfrastructure.EntandoClusterInfrastructureController;
import org.entando.kubernetes.controller.clusterinfrastructure.EntandoK8SServiceDeployableContainer;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.model.DbmsVendor;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.entando.kubernetes.test.e2etest.common.SampleWriter;
import org.entando.kubernetes.test.e2etest.helpers.K8SIntegrationTestHelper;
import org.entando.kubernetes.test.e2etest.helpers.KeycloakE2ETestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("end-to-end"), @Tag("inter-process"), @Tag("post-deployment")})
class AddClusterInfrastructureIT implements FluentIntegrationTesting {

    private static final String CLUSTER_INFRASTRUCTURE_NAME = EntandoOperatorTestConfig.calculateName("eti");
    protected K8SIntegrationTestHelper helper = new K8SIntegrationTestHelper();
    protected DefaultKubernetesClient client = helper.getClient();
    protected EntandoClusterInfrastructureController controller = new EntandoClusterInfrastructureController(client, false);
    private ClusterInfrastructureIntegrationTestHelper clusterInfrastructure = new ClusterInfrastructureIntegrationTestHelper(client);

    @BeforeEach
    public void cleanup() {
        client = helper.getClient();
        helper.keycloak().prepareDefaultKeycloakSecretAndConfigMap();
        helper.keycloak().deleteRealm(KeycloakE2ETestHelper.KEYCLOAK_REALM);
        helper.setTextFixture(
                deleteAll(EntandoClusterInfrastructure.class)
                        .fromNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                        .deleteAll(EntandoKeycloakServer.class)
                        .fromNamespace(KeycloakE2ETestHelper.KEYCLOAK_NAMESPACE));
        helper.externalDatabases().deletePgTestPod(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE);
        if (EntandoOperatorTestConfig.getTestTarget() == TestTarget.K8S) {
            clusterInfrastructure()
                    .listenAndRespondWithImageVersionUnderTest(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE);
        } else {
            clusterInfrastructure()
                    .listenAndRespondWithStartupEvent(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE,
                            controller::onStartup);
        }
    }

    private ClusterInfrastructureIntegrationTestHelper clusterInfrastructure() {
        return this.clusterInfrastructure;
    }

    @Test
    void create() {
        //When I create an EntandoClusterInfrastructure and I specify it to use PostgreSQL

        EntandoClusterInfrastructure clusterInfrastructure = new EntandoClusterInfrastructureBuilder().withNewMetadata()
                .withNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(CLUSTER_INFRASTRUCTURE_NAME)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsVendor.POSTGRESQL)//Ignore atm
                .withDefault(true)
                .withNewKeycloakToUse()
                .withRealm(KeycloakE2ETestHelper.KEYCLOAK_REALM)
                .endKeycloakToUse()
                .withReplicas(1)
                .withIngressHostName(CLUSTER_INFRASTRUCTURE_NAME + "."
                        + helper
                        .getDomainSuffix()).endSpec().build();
        SampleWriter.writeSample(clusterInfrastructure, "entando-cluster-infrastructure-with-embedded-postgresql-db");
        clusterInfrastructure().waitForClusterInfrastructure(clusterInfrastructure, 30, true);

        //Then I expect to see
        verifyK8sServiceDeployment();
        verifyConnectionConfigCreation(clusterInfrastructure);
    }

    @AfterEach
    public void afterwards() {
        helper.afterTest();
    }

    protected void verifyK8sServiceDeployment() {
        await().atMost(15, TimeUnit.SECONDS).until(() -> HttpTestHelper.statusOk(
                HttpTestHelper.getDefaultProtocol() + "://" + CLUSTER_INFRASTRUCTURE_NAME + "."
                        + helper
                        .getDomainSuffix()
                        + "/k8s/actuator/health"));
        Deployment k8sSvcDeployment = client.apps().deployments()
                .inNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(
                        CLUSTER_INFRASTRUCTURE_NAME + "-k8s-svc-deployment")
                .get();
        assertThat(thePortNamed("k8s-svc-port")
                .on(theContainerNamed("k8s-svc-container")
                        .on(k8sSvcDeployment))
                .getContainerPort(), is(8084));
        Service service = client.services()
                .inNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(
                        CLUSTER_INFRASTRUCTURE_NAME + "-k8s-svc-service")
                .get();
        assertThat(thePortNamed("k8s-svc-port").on(service).getPort(), is(8084));
        assertTrue(k8sSvcDeployment.getStatus().getReadyReplicas() >= 1);
        assertTrue(clusterInfrastructure().getOperations()
                .inNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(CLUSTER_INFRASTRUCTURE_NAME)
                .fromServer().get().getStatus().forServerQualifiedBy("k8s-svc").isPresent());
        String k8sServiceClientId = CLUSTER_INFRASTRUCTURE_NAME + "-"
                + EntandoK8SServiceDeployableContainer.K8S_SVC_QUALIFIER;
        assertTrue(helper.keycloak().findClientById(KeycloakE2ETestHelper.KEYCLOAK_REALM, k8sServiceClientId).isPresent());

    }

    private void verifyConnectionConfigCreation(EntandoClusterInfrastructure infrastructure) {
        ConfigMap configMap = client.configMaps().inNamespace(infrastructure.getMetadata().getNamespace())
                .withName(InfrastructureConfig.connectionConfigMapNameFor(infrastructure)).get();
        assertNotNull(configMap.getData().get(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY));
        assertNotNull(configMap.getData().get(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY));
        assertNotNull(configMap.getData().get(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY));
    }

}

package org.entando.kubernetes.controller.clusterinfrastructure.interprocesstests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import java.util.concurrent.TimeUnit;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.clusterinfrastructure.EntandoClusterInfrastructureController;
import org.entando.kubernetes.controller.clusterinfrastructure.EntandoK8SServiceDeployableContainer;
import org.entando.kubernetes.controller.common.TlsHelper;
import org.entando.kubernetes.controller.integrationtest.support.ClusterInfrastructureIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig;
import org.entando.kubernetes.controller.integrationtest.support.EntandoOperatorTestConfig.TestTarget;
import org.entando.kubernetes.controller.integrationtest.support.FluentIntegrationTesting;
import org.entando.kubernetes.controller.integrationtest.support.HttpTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.K8SIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.KeycloakIntegrationTestHelper;
import org.entando.kubernetes.controller.integrationtest.support.SampleWriter;
import org.entando.kubernetes.model.DbmsImageVendor;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.model.keycloakserver.EntandoKeycloakServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tags({@Tag("end-to-end"), @Tag("inter-process")})
public class AddClusterInfrastructureIT implements FluentIntegrationTesting {

    private static final String CLUSTER_INFRASTRUCTURE_NAME = EntandoOperatorTestConfig.calculateName("eti");
    protected K8SIntegrationTestHelper helper = new K8SIntegrationTestHelper();
    protected DefaultKubernetesClient client = helper.getClient();
    protected EntandoClusterInfrastructureController controller = new EntandoClusterInfrastructureController(client, false);

    @BeforeEach
    public void cleanup() {
        client = helper.getClient();
        helper.setTextFixture(
                deleteAll(EntandoClusterInfrastructure.class)
                        .fromNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                        .deleteAll(EntandoKeycloakServer.class)
                        .fromNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE));
        await().atMost(2, TimeUnit.MINUTES).ignoreExceptions().pollInterval(10, TimeUnit.SECONDS).until(this::killPgPod);
        if (EntandoOperatorTestConfig.getTestTarget() == TestTarget.K8S) {
            helper.clusterInfrastructure()
                    .listenAndRespondWithImageVersionUnderTest(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE);
        } else {
            helper.clusterInfrastructure()
                    .listenAndRespondWithStartupEvent(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE,
                            controller::onStartup);
        }
        helper.keycloak().listenAndRespondWithLatestImage(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE);
    }

    private boolean killPgPod() {
        PodResource<Pod, DoneablePod> resource = client.pods()
                .inNamespace(KeycloakIntegrationTestHelper.KEYCLOAK_NAMESPACE).withName("pg-test");
        if (resource.fromServer().get() == null) {
            return true;
        }
        resource.delete();
        return false;
    }

    @Test
    public void create() {
        //When I create an EntandoClusterInfrastructure and I specify it to use PostgreSQL

        EntandoClusterInfrastructure clusterInfrastructure = new EntandoClusterInfrastructureBuilder().withNewMetadata()
                .withNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(CLUSTER_INFRASTRUCTURE_NAME)
                .endMetadata()
                .withNewSpec()
                .withDbms(DbmsImageVendor.POSTGRESQL)//Ignore atm
                .withDefault(true)
                .withReplicas(1)
                .withIngressHostName(CLUSTER_INFRASTRUCTURE_NAME + "."
                        + helper
                        .getDomainSuffix()).endSpec().build();
        SampleWriter.writeSample(clusterInfrastructure, "entando-cluster-infrastructure-with-embedded-postgresql-db");
        helper.createAndWaitForClusterInfrastructure(clusterInfrastructure, 30, true);
        //Then I expect to see
        verifyK8sServiceDeployment();
        verifySecretCreation();
    }

    @AfterEach
    public void afterwards() {
        helper.afterTest();
    }

    protected void verifyK8sServiceDeployment() {
        await().atMost(15, TimeUnit.SECONDS).until(() -> HttpTestHelper.statusOk(
                TlsHelper.getDefaultProtocol() + "://" + CLUSTER_INFRASTRUCTURE_NAME + "."
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
        assertTrue(helper.clusterInfrastructure().getOperations()
                .inNamespace(ClusterInfrastructureIntegrationTestHelper.CLUSTER_INFRASTRUCTURE_NAMESPACE)
                .withName(CLUSTER_INFRASTRUCTURE_NAME)
                .fromServer().get().getStatus().forServerQualifiedBy("k8s-svc").isPresent());
        String k8sServiceClientId = CLUSTER_INFRASTRUCTURE_NAME + "-"
                + EntandoK8SServiceDeployableContainer.K8S_SVC_QUALIFIER;
        assertTrue(helper.keycloak().findClientById(k8sServiceClientId).isPresent());

    }

    private void verifySecretCreation() {
        Secret infrastructureSecret = client.secrets()
                .withName(EntandoOperatorConfig.getEntandoInfrastructureSecretName()).get();
        assertNotNull(infrastructureSecret.getData().get("entandoK8SServiceInternalUrl"));
        assertNotNull(infrastructureSecret.getData().get("entandoK8SServiceInternalUrl"));
        assertNotNull(infrastructureSecret.getData().get("entandoK8SServiceClientId"));
    }

}

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

package org.entando.kubernetes.controller.clusterinfrastructure.inprocesstests;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.quarkus.runtime.StartupEvent;
import java.util.Collections;
import java.util.Map;
import org.entando.kubernetes.controller.clusterinfrastructure.EntandoClusterInfrastructureController;
import org.entando.kubernetes.controller.spi.common.NameUtils;
import org.entando.kubernetes.controller.spi.container.KeycloakClientConfig;
import org.entando.kubernetes.controller.spi.container.KeycloakName;
import org.entando.kubernetes.controller.support.client.InfrastructureConfig;
import org.entando.kubernetes.controller.support.client.SimpleK8SClient;
import org.entando.kubernetes.controller.support.client.SimpleKeycloakClient;
import org.entando.kubernetes.controller.support.client.doubles.EntandoResourceClientDouble;
import org.entando.kubernetes.controller.support.client.doubles.SimpleK8SClientDouble;
import org.entando.kubernetes.controller.support.common.KubeUtils;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructureBuilder;
import org.entando.kubernetes.test.common.FluentTraversals;
import org.entando.kubernetes.test.common.InProcessTestData;
import org.entando.kubernetes.test.componenttest.InProcessTestUtil;
import org.entando.kubernetes.test.componenttest.argumentcaptors.KeycloakClientConfigArgumentCaptor;
import org.entando.kubernetes.test.componenttest.argumentcaptors.NamedArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tags({@Tag("in-process"), @Tag("pre-deployment"), @Tag("component")})
//Because SONAR can't recognize custom matchers
@SuppressWarnings({"java:S6068", "java:S6073"})
class DeployEntandoClusterInfrastructureTest implements InProcessTestUtil, FluentTraversals {

    public static final int PORT_8084 = 8084;
    private static final String MY_CLUSTER_INFRASTRUCTURE_INGRESS = MY_CLUSTER_INFRASTRUCTURE + "-" + NameUtils.DEFAULT_INGRESS_SUFFIX;

    private static final String MY_CLUSTER_INFRASTRUCTURE_K8S_SVC = MY_CLUSTER_INFRASTRUCTURE + "-k8s-svc";
    private static final String MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SERVICE = MY_CLUSTER_INFRASTRUCTURE_K8S_SVC + "-service";
    private static final String K8S = "/k8s";
    private static final String K8S_SERVICE_PORT = "k8s-svc-port";
    private static final String MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SECRET = MY_CLUSTER_INFRASTRUCTURE_K8S_SVC + "-secret";
    private static final String MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_DEPLOYMENT = MY_CLUSTER_INFRASTRUCTURE_K8S_SVC + "-deployment";
    public static final String PARAMETER_VALUE = "MY_VALUE";
    public static final String PARAMETER_NAME = "MY_PARAM";
    private final EntandoClusterInfrastructure entandoClusterInfrastructure = new EntandoClusterInfrastructureBuilder(
            newEntandoClusterInfrastructure()).editSpec()
            .withEnvironmentVariables(Collections.singletonList(new EnvVar(PARAMETER_NAME, PARAMETER_VALUE, null)))
            .endSpec().build();

    @Spy
    private final SimpleK8SClient<EntandoResourceClientDouble> client = new SimpleK8SClientDouble();
    @Mock
    private SimpleKeycloakClient keycloakClient;
    private EntandoClusterInfrastructureController entandoClusterInfrastructureController;

    @BeforeEach
    public void before() {
        entandoClusterInfrastructureController = new EntandoClusterInfrastructureController(client, keycloakClient);
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_ACTION, Action.ADDED.name());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAMESPACE, entandoClusterInfrastructure.getMetadata().getNamespace());
        System.setProperty(KubeUtils.ENTANDO_RESOURCE_NAME, entandoClusterInfrastructure.getMetadata().getName());
        client.entandoResources().createOrPatchEntandoResource(entandoClusterInfrastructure);

    }

    @Test
    void testSecrets() {
        //Given I have an EntandoClusterInfrastructure custom resource specifying a Wildfly database
        final EntandoClusterInfrastructure newEntandoClusterInfrastructure = this.entandoClusterInfrastructure;
        //And a Keycloak instance is available
        emulateKeycloakDeployment(client);
        //And Keycloak is receiving requests
        when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn(KEYCLOAK_SECRET);

        //When the EntandoClusterInfrastructureController is notified of the creation of the EntandoClusterInfrastructure
        entandoClusterInfrastructureController.onStartup(new StartupEvent());

        //Then a K8S Secret was created with a name that reflects the EntandoClusterInfrastructure and the fact that it is a Keycloak secret
        NamedArgumentCaptor<Secret> entandoK8SServiceKeycloakSecretCaptor = forResourceNamed(Secret.class,
                MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SECRET);
        verify(client.secrets(), atLeastOnce())
                .createSecretIfAbsent(eq(newEntandoClusterInfrastructure), entandoK8SServiceKeycloakSecretCaptor.capture());
        Secret entandoK8SServiceKeycloakSecret = entandoK8SServiceKeycloakSecretCaptor.getValue();
        assertThat(entandoK8SServiceKeycloakSecret.getStringData().get(KeycloakName.CLIENT_ID_KEY),
                is(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC));
        assertThat(entandoK8SServiceKeycloakSecret.getStringData().get(KeycloakName.CLIENT_SECRET_KEY), is(KEYCLOAK_SECRET));

        //Then a K8S ConfigMap was created in the controllers' namespace pointing to various locations
        NamedArgumentCaptor<ConfigMap> infrastructureUrlsSecretCaptor = forResourceNamed(ConfigMap.class,
                InfrastructureConfig.connectionConfigMapNameFor(newEntandoClusterInfrastructure));
        verify(client.secrets(), atLeastOnce())
                .createConfigMapIfAbsent(eq(newEntandoClusterInfrastructure), infrastructureUrlsSecretCaptor.capture());
        ConfigMap infrastructureUrlsSecret = infrastructureUrlsSecretCaptor.getValue();
        assertThat(infrastructureUrlsSecret.getData().get(InfrastructureConfig.ENTANDO_K8S_SERVICE_CLIENT_ID_KEY),
                is(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC));
        assertThat(infrastructureUrlsSecret.getData().get(InfrastructureConfig.ENTANDO_K8S_SERVICE_INTERNAL_URL_KEY),
                is("http://" + MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SERVICE
                        + "." + MY_CLUSTER_INFRASTRUCTURE_NAMESPACE + ".svc.cluster.local:8084/k8s"));
        assertThat(infrastructureUrlsSecret.getData().get(InfrastructureConfig.ENTANDO_K8S_SERVICE_EXTERNAL_URL_KEY),
                is("https://entando-infra.192.168.0.100.nip.io/k8s"));
        //And the Operator's default ConfigMap points to the previously created EntandoClusterInfrastructure as default
        assertThat(client.entandoResources().loadDefaultCapabilitiesConfigMap().getData()
                .get(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAME_KEY), is(MY_CLUSTER_INFRASTRUCTURE));
        assertThat(client.entandoResources().loadDefaultCapabilitiesConfigMap().getData()
                .get(InfrastructureConfig.DEFAULT_CLUSTER_INFRASTRUCTURE_NAMESPACE_KEY), is(MY_CLUSTER_INFRASTRUCTURE_NAMESPACE));
    }

    @Test
    void testService() {
        //Given I have an  EntandoClusterInfrastructure custom resource specifying a Wildfly database
        final EntandoClusterInfrastructure newEntandoClusterInfrastructure = this.entandoClusterInfrastructure;
        final ServiceStatus entandoK8SServiceServiceStatus = new ServiceStatus();
        lenient()
                .when(client.services().loadService(eq(newEntandoClusterInfrastructure), eq(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SERVICE)))
                .then(respondWithServiceStatus(entandoK8SServiceServiceStatus));
        //And a Keycloak instance is available
        emulateKeycloakDeployment(client);

        //When the EntandoClusterInfrastructureController is notified of the creation of the KeycloakServer
        entandoClusterInfrastructureController.onStartup(new StartupEvent());
        verifyEntandoK8SService(newEntandoClusterInfrastructure, entandoK8SServiceServiceStatus);

    }

    private void verifyEntandoK8SService(EntandoClusterInfrastructure newEntandoClusterInfrastructure,
            ServiceStatus expectedServiceStatus) {
        //Then a Digital Exchange was created with a name that reflects the EntandoClusterInfrastructure and the fact that it is a JEE
        // service
        NamedArgumentCaptor<Service> serviceCaptor = forResourceNamed(Service.class, MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SERVICE);
        verify(client.services()).createOrReplaceService(eq(newEntandoClusterInfrastructure), serviceCaptor.capture());
        Service resultingService = serviceCaptor.getValue();
        //And a selector that matches the EntandoClusterInfrastructure  pods
        Map<String, String> selector = resultingService.getSpec().getSelector();
        assertThat(selector.get(DEPLOYMENT_LABEL_NAME), is(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC));
        assertThat(selector.get(ENTANDO_CLUSTER_INFRASTRUCTURE_LABEL_NAME), is(MY_CLUSTER_INFRASTRUCTURE));
        //And the TCP port 8080 named 'k8s-svc-port'
        assertThat(thePortNamed(K8S_SERVICE_PORT).on(resultingService).getPort(), is(PORT_8084));
        assertThat(thePortNamed(K8S_SERVICE_PORT).on(resultingService).getTargetPort().getIntVal(), is(PORT_8084));
        assertThat(thePortNamed(K8S_SERVICE_PORT).on(resultingService).getProtocol(), is(TCP));
        //And the Service state was reloaded from K8S
        verify(client.services()).loadService(eq(newEntandoClusterInfrastructure), eq(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SERVICE));

        //And K8S was instructed to update the status of the EntandoClusterInfrastructure with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoClusterInfrastructure), argThat(matchesServiceStatus(expectedServiceStatus)));
    }

    @Test
    void testIngress() {
        //Given I have an  EntandoClusterInfrastructure custom resource specifying a Wildfly database
        final EntandoClusterInfrastructure newEntandoClusterInfrastructure = this.entandoClusterInfrastructure;
        //And a Keycloak instance is available
        emulateKeycloakDeployment(client);

        //When the EntandoClusterInfrastructureController is notified of the creation of the  EntandoClusterInfrastructure
        entandoClusterInfrastructureController.onStartup(new StartupEvent());
        //Then the Ingress state was reloaded from K8S
        verify(client.ingresses(), atLeast(2))
                .loadIngress(eq(newEntandoClusterInfrastructure.getMetadata().getNamespace()), eq(MY_CLUSTER_INFRASTRUCTURE_INGRESS));

        verifyEntandoK8SServiceIngressPath(newEntandoClusterInfrastructure);
    }

    private void verifyEntandoK8SServiceIngressPath(EntandoClusterInfrastructure newEntandoClusterInfrastructure) {
        final NamedArgumentCaptor<Ingress> ingressArgumentCaptor = forResourceNamed(Ingress.class, MY_CLUSTER_INFRASTRUCTURE_INGRESS);
        verify(client.ingresses()).createIngress(eq(newEntandoClusterInfrastructure), ingressArgumentCaptor.capture());
        final Ingress resultingIngress = ingressArgumentCaptor.getValue();
        //With a path that reflects webcontext of the EntandoK8sSvc
        theHttpPath(K8S).on(resultingIngress);
        //that is mapped to the previously created HTTP service
        assertThat(theBackendFor(K8S).on(resultingIngress).getService().getPort().getNumber(), is(PORT_8084));
        assertThat(theBackendFor(K8S).on(resultingIngress).getService().getName(), is(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SERVICE));
    }

    @Test
    void testDeployment() {
        //Given I have an KeycloakServer custom resource specifying a Wildfly database
        final EntandoClusterInfrastructure newEntandoClusterInfrastructure = this.entandoClusterInfrastructure;
        //And a Keycloak instance is available
        emulateKeycloakDeployment(client);
        //And K8S is receiving Deployment requests
        final DeploymentStatus entandok8SServiceDeploymentStatus = new DeploymentStatus();
        lenient()
                .when(client.deployments().loadDeployment(eq(newEntandoClusterInfrastructure),
                        eq(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_DEPLOYMENT)))
                .then(respondWithDeploymentStatus(entandok8SServiceDeploymentStatus));
        //And Keycloak is receiving requests
        when(keycloakClient.prepareClientAndReturnSecret(any(KeycloakClientConfig.class))).thenReturn(KEYCLOAK_SECRET);
        //When the EntandoClusterInfrastructureController is notified of the creation of the KeycloakServer
        entandoClusterInfrastructureController.onStartup(new StartupEvent());

        verify(keycloakClient, times(1))
                .login(eq(InProcessTestData.MY_KEYCLOAK_BASE_URL), eq(MY_KEYCLOAK_ADMIN_USERNAME), anyString());
        verifyEntandoK8SServiceDeployment(newEntandoClusterInfrastructure, entandok8SServiceDeploymentStatus);

    }

    private void verifyEntandoK8SServiceDeployment(EntandoClusterInfrastructure newEntandoClusterInfrastructure,
            DeploymentStatus deploymentStatus) {
        // Then a K8S deployment is created with a name that reflects the EntandoClusterInfrastructure name and
        // the fact that it is the EntandoK8SService deployment
        NamedArgumentCaptor<Deployment> deploymentCaptor = forResourceNamed(Deployment.class,
                MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_DEPLOYMENT);
        verify(client.deployments(), atLeastOnce())
                .createOrPatchDeployment(eq(newEntandoClusterInfrastructure), deploymentCaptor.capture());
        Deployment resultingDeployment = deploymentCaptor.getValue();
        //With a Pod Template that has labels linking it to the previously created K8S Service
        Map<String, String> selector = resultingDeployment.getSpec().getTemplate().getMetadata().getLabels();
        assertThat(selector.get(DEPLOYMENT_LABEL_NAME), is(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC));
        assertThat(selector.get(ENTANDO_CLUSTER_INFRASTRUCTURE_LABEL_NAME), is(MY_CLUSTER_INFRASTRUCTURE));
        //Exposing a port 8080
        Container theUserManagementContainer = theContainerNamed("k8s-svc-container")
                .on(resultingDeployment);
        assertThat(thePortNamed(K8S_SERVICE_PORT).on(theUserManagementContainer).getContainerPort(), is(PORT_8084));
        assertThat(thePortNamed(K8S_SERVICE_PORT).on(theUserManagementContainer).getProtocol(), is(TCP));
        //And that uses the image reflecting the Entando image
        assertThat(theUserManagementContainer.getImage(), containsString("entando/entando-k8s-service"));
        //And Keycloak was configured to support OIDC Integration from the EntandoClusterInfrastructure
        KeycloakClientConfigArgumentCaptor keycloakClientConfigCaptor = forClientId(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC);
        verify(keycloakClient).prepareClientAndReturnSecret(keycloakClientConfigCaptor.capture());
        KeycloakClientConfig keycloakClientConfig = keycloakClientConfigCaptor.getValue();
        assertThat(keycloakClientConfig.getRealm(), is("entando"));

        //And is configured to use the previously installed Keycloak instance
        verifyKeycloakSettings(thePrimaryContainerOn(resultingDeployment), MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SECRET);
        verifySpringSecuritySettings(thePrimaryContainerOn(resultingDeployment), MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_SECRET);
        assertThat(theVariableNamed("SERVER_SERVLET_CONTEXT_PATH").on(thePrimaryContainerOn(resultingDeployment)), is(K8S));
        assertThat(theVariableNamed(PARAMETER_NAME).on(thePrimaryContainerOn(resultingDeployment)), is(PARAMETER_VALUE));

        //And the Deployment state was reloaded from K8S
        verify(client.deployments())
                .loadDeployment(eq(newEntandoClusterInfrastructure), eq(MY_CLUSTER_INFRASTRUCTURE_K8S_SVC_DEPLOYMENT));
        //And K8S was instructed to update the status of the EntandoClusterInfrastructure with the status of the service
        verify(client.entandoResources(), atLeastOnce())
                .updateStatus(eq(newEntandoClusterInfrastructure), argThat(matchesDeploymentStatus(deploymentStatus)));
    }

}

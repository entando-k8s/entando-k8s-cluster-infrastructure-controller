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
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.SimpleKeycloakClient;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
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

    protected void processAddition(EntandoClusterInfrastructure newEntandoClusterInfrastructure) {
        KeycloakConnectionConfig keycloakConnectionConfig = k8sClient.entandoResources().findKeycloak(newEntandoClusterInfrastructure);
        deployDigitalExchange(newEntandoClusterInfrastructure, keycloakConnectionConfig);

        ServiceDeploymentResult entandoK8SService = deployEntandoK8SService(newEntandoClusterInfrastructure,
                keycloakConnectionConfig);
        if (newEntandoClusterInfrastructure.getSpec().isDefault()) {
            k8sClient.secrets().overwriteControllerSecret(new SecretBuilder()
                    .withNewMetadata()
                    .withName(EntandoOperatorConfig.getEntandoInfrastructureSecretName())
                    .endMetadata()
                    .addToStringData("entandoK8SServiceClientId", clientIdOf(newEntandoClusterInfrastructure))
                    .addToStringData("entandoK8SServiceInternalUrl", entandoK8SService.getInternalBaseUrl())
                    .addToStringData("entandoK8SServiceExternalUrl", entandoK8SService.getExternalBaseUrl())
                    .build());
        }
    }

    protected void deployDigitalExchange(EntandoClusterInfrastructure newEntandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        DatabaseServiceResult databaseServiceResult = prepareDatabaseService(newEntandoClusterInfrastructure,
                newEntandoClusterInfrastructure.getSpec().getDbms(), "digexdb");
        DigitalExchangeDeployable digitalExchangeDeployable = new DigitalExchangeDeployable(newEntandoClusterInfrastructure,
                keycloakConnectionConfig, databaseServiceResult);
        new DeployCommand<>(digitalExchangeDeployable).execute(k8sClient, Optional.of(keycloakClient));
    }

    private ServiceDeploymentResult deployEntandoK8SService(EntandoClusterInfrastructure newEntandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        EntandoK8SServiceDeployable deployable = new EntandoK8SServiceDeployable(newEntandoClusterInfrastructure, keycloakConnectionConfig);
        DeployCommand<ServiceDeploymentResult> command = new DeployCommand<>(deployable);
        ServiceDeploymentResult result = command.execute(k8sClient, Optional.of(keycloakClient));
        k8sClient.entandoResources().updateStatus(newEntandoClusterInfrastructure, command.getStatus());
        return result;
    }

}

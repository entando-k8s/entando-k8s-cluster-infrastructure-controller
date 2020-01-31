package org.entando.kubernetes.controller.clusterinfrastructure;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.KubernetesPermission;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.plugin.ExpectedRole;

public class EntandoK8SServiceDeployableContainer implements SpringBootDeployableContainer {

    public static final String K8S_SVC_QUALIFIER = "k8s-svc";
    private static final String ENTANDO_K8S_SERVICE_IMAGE_NAME = "entando/entando-k8s-service";

    private final EntandoClusterInfrastructure entandoClusterInfrastructure;
    private final KeycloakConnectionConfig keycloakConnectionConfig;

    public EntandoK8SServiceDeployableContainer(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        this.entandoClusterInfrastructure = entandoClusterInfrastructure;
        this.keycloakConnectionConfig = keycloakConnectionConfig;
    }

    public static String clientIdOf(EntandoClusterInfrastructure infrastructure) {
        return infrastructure.getMetadata().getName() + "-" + K8S_SVC_QUALIFIER;
    }

    @Override
    public DatabaseSchemaCreationResult getDatabaseSchema() {
        return null;
    }

    @Override
    public int getCpuLimitMillicores() {
        return 1000;
    }

    @Override
    public int getMemoryLimitMebibytes() {
        return 768;
    }

    @Override
    public String determineImageToUse() {
        return ENTANDO_K8S_SERVICE_IMAGE_NAME;
    }

    @Override
    public String getNameQualifier() {
        return K8S_SVC_QUALIFIER;
    }

    @Override
    public int getPort() {
        return 8084;
    }

    public KeycloakConnectionConfig getKeycloakConnectionConfig() {
        return keycloakConnectionConfig;
    }

    @Override
    public KeycloakClientConfig getKeycloakClientConfig() {
        String clientId = clientIdOf(this.entandoClusterInfrastructure);
        List<ExpectedRole> clientRoles = Arrays.asList(
                new ExpectedRole(KubeUtils.ENTANDO_APP_ROLE),
                new ExpectedRole(KubeUtils.ENTANDO_PLUGIN_ROLE)
        );
        return new KeycloakClientConfig(KubeUtils.ENTANDO_KEYCLOAK_REALM,
                clientId, "Entando K8S Service", clientRoles, null);
    }

    @Override
    public String getWebContextPath() {
        return "/k8s";
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath() + "/actuator/health");
    }

    @Override
    public List<KubernetesPermission> getKubernetesPermissions() {
        return Arrays.asList(new KubernetesPermission("entando.org", "*", "*"),
                new KubernetesPermission("", "secrets", "create", "get", "update", "delete"),
                new KubernetesPermission("", "configmaps", "*"),
                new KubernetesPermission("", "namespaces", "get")
        );
    }

    @Override
    public List<String> getDbSchemaQualifiers() {
        return Collections.emptyList();
    }

    @Override
    public Optional<DatabasePopulator> useDatabaseSchemas(Map<String, DatabaseSchemaCreationResult> map) {
        return Optional.empty();
    }
}

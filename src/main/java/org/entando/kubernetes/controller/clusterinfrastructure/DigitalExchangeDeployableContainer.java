package org.entando.kubernetes.controller.clusterinfrastructure;

import java.util.Optional;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.common.EntandoDatabaseConsumingContainer;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.IngressingContainer;
import org.entando.kubernetes.controller.spi.KeycloakAware;
import org.entando.kubernetes.controller.spi.PersistentVolumeAware;
import org.entando.kubernetes.controller.spi.TlsAware;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public class DigitalExchangeDeployableContainer extends EntandoDatabaseConsumingContainer implements IngressingContainer,
        PersistentVolumeAware, KeycloakAware, TlsAware {

    public static final String INGRESS_WEB_CONTEXT = "/digital-exchange";
    public static final int PORT = 8080;
    public static final String DIGITAL_EXCHANGE_QUALIFIER = "dig-ex";
    private final EntandoClusterInfrastructure entandoClusterInfrastructure;
    private final KeycloakConnectionConfig keycloakConnectionConfig;

    public DigitalExchangeDeployableContainer(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        this.entandoClusterInfrastructure = entandoClusterInfrastructure;
        this.keycloakConnectionConfig = keycloakConnectionConfig;
    }

    public static String clientIdOf(EntandoClusterInfrastructure entandoClusterInfrastructure) {
        //TOOD may have to prefix namespace
        return entandoClusterInfrastructure.getMetadata().getName() + "-" + DIGITAL_EXCHANGE_QUALIFIER;
    }

    @Override
    public int getCpuLimitMillicores() {
        return 1000;
    }

    @Override
    public int getMemoryLimitMebibytes() {
        return 1024 + 768;
    }

    @Override
    public String determineImageToUse() {
        return "entando/digital-exchange";
    }

    @Override
    public String getNameQualifier() {
        return DIGITAL_EXCHANGE_QUALIFIER;
    }

    @Override
    public int getPort() {
        return PORT;
    }

    public KeycloakConnectionConfig getKeycloakConnectionConfig() {
        return keycloakConnectionConfig;
    }

    @Override
    public KeycloakClientConfig getKeycloakClientConfig() {
        String clientId = clientIdOf(this.entandoClusterInfrastructure);
        return new KeycloakClientConfig(KubeUtils.ENTANDO_KEYCLOAK_REALM,
                clientId,
                clientId).withRole("superuser").withPermission("realm-management", "realm-admin");
    }

    @Override
    public String getWebContextPath() {
        return INGRESS_WEB_CONTEXT;
    }

    @Override
    public Optional<String> getHealthCheckPath() {
        return Optional.of(getWebContextPath());
    }

    @Override
    public String getVolumeMountPath() {
        return "/entando-data";
    }

    @Override
    protected DatabasePopulator buildDatabasePopulator() {
        return new DigitalExchangeDatabasePopulator(this);
    }
}

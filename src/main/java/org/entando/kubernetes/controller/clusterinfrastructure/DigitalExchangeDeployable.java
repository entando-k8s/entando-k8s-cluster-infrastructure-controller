package org.entando.kubernetes.controller.clusterinfrastructure;

import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.database.DatabaseServiceResult;
import org.entando.kubernetes.controller.spi.DbAwareDeployable;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public class DigitalExchangeDeployable extends InfrastructureDeployableBase implements DbAwareDeployable {

    public static final String STRING = "dig-ex";
    private final List<DeployableContainer> containers;
    private final DatabaseServiceResult databaseServiceResult;

    public DigitalExchangeDeployable(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig,
            DatabaseServiceResult databaseServiceResult) {
        super(entandoClusterInfrastructure);
        this.databaseServiceResult = databaseServiceResult;
        containers = Arrays.asList(new DigitalExchangeDeployableContainer(entandoClusterInfrastructure, keycloakConnectionConfig));
    }

    @Override
    public DatabaseServiceResult getDatabaseServiceResult() {
        return databaseServiceResult;
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return STRING;
    }

}

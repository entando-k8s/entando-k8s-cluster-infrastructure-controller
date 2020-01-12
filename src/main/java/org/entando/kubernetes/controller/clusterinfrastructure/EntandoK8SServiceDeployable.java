package org.entando.kubernetes.controller.clusterinfrastructure;

import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.spi.DeployableContainer;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public class EntandoK8SServiceDeployable extends InfrastructureDeployableBase {

    private final List<DeployableContainer> containers;

    public EntandoK8SServiceDeployable(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        super(entandoClusterInfrastructure);
        containers = Arrays.asList(new EntandoK8SServiceDeployableContainer(entandoClusterInfrastructure, keycloakConnectionConfig));
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return "k8s-svc";
    }

}

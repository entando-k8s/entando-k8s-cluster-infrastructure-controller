package org.entando.kubernetes.controller.clusterinfrastructure;

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.List;
import org.entando.kubernetes.controller.spi.DatabasePopulator;

public class DigitalExchangeDatabasePopulator implements DatabasePopulator {

    private final DigitalExchangeDeployableContainer digitalExchangeDeployableContainer;

    public DigitalExchangeDatabasePopulator(DigitalExchangeDeployableContainer digitalExchangeDeployableContainer) {
        this.digitalExchangeDeployableContainer = digitalExchangeDeployableContainer;
    }

    @Override
    public String determineImageToUse() {
        return digitalExchangeDeployableContainer.determineImageToUse();
    }

    @Override
    public String[] getCommand() {
        return new String[]{"/bin/bash", "-c", "/entando-common/init-db-from-deployment.sh"};
    }

    @Override
    public void addEnvironmentVariables(List<EnvVar> vars) {
        digitalExchangeDeployableContainer.addEnvironmentVariables(vars);
    }

}

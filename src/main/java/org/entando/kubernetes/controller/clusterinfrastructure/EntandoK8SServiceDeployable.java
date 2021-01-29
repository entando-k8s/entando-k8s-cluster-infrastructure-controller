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

package org.entando.kubernetes.controller.clusterinfrastructure;

import java.util.Arrays;
import java.util.List;
import org.entando.kubernetes.controller.spi.container.DeployableContainer;
import org.entando.kubernetes.controller.spi.container.KeycloakConnectionConfig;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public class EntandoK8SServiceDeployable extends InfrastructureDeployableBase {

    private final List<DeployableContainer> containers;

    public EntandoK8SServiceDeployable(EntandoClusterInfrastructure entandoClusterInfrastructure,
            KeycloakConnectionConfig keycloakConnectionConfig) {
        super(entandoClusterInfrastructure);
        containers = Arrays.asList(new EntandoK8SServiceDeployableContainer(entandoClusterInfrastructure, keycloakConnectionConfig));
    }

    @Override
    public String getDefaultServiceAccountName() {
        return "entando-k8s-service";
    }

    @Override
    public int getReplicas() {
        return super.entandoClusterInfrastructure.getSpec().getReplicas().orElse(1);
    }

    @Override
    public List<DeployableContainer> getContainers() {
        return containers;
    }

    @Override
    public String getNameQualifier() {
        return "k8s-svc";
    }

    @Override
    public String getServiceAccountToUse() {
        return this.entandoClusterInfrastructure.getSpec().getServiceAccountToUse().orElse(getDefaultServiceAccountName());
    }

}

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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public abstract class InfrastructureDeployableBase implements
        IngressingDeployable<ClusterInfrastructureDeploymentResult, EntandoClusterInfrastructure> {

    protected final EntandoClusterInfrastructure entandoClusterInfrastructure;

    protected InfrastructureDeployableBase(
            EntandoClusterInfrastructure entandoClusterInfrastructure) {
        this.entandoClusterInfrastructure = entandoClusterInfrastructure;
    }

    @Override
    public final EntandoClusterInfrastructure getCustomResource() {
        return entandoClusterInfrastructure;
    }

    @Override
    public final ClusterInfrastructureDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new ClusterInfrastructureDeploymentResult(pod, service, ingress);
    }

    @Override
    public final String getIngressName() {
        return KubeUtils.standardIngressName(entandoClusterInfrastructure);
    }

    @Override
    public final String getIngressNamespace() {
        return entandoClusterInfrastructure.getMetadata().getNamespace();
    }
}

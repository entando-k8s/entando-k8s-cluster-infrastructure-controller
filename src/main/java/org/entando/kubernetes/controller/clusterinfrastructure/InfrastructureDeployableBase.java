package org.entando.kubernetes.controller.clusterinfrastructure;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.ServiceDeploymentResult;
import org.entando.kubernetes.controller.spi.IngressingDeployable;
import org.entando.kubernetes.model.EntandoCustomResource;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;

public abstract class InfrastructureDeployableBase implements IngressingDeployable<ServiceDeploymentResult> {

    protected final EntandoClusterInfrastructure entandoClusterInfrastructure;

    public InfrastructureDeployableBase(
            EntandoClusterInfrastructure entandoClusterInfrastructure) {
        this.entandoClusterInfrastructure = entandoClusterInfrastructure;
    }

    @Override
    public final EntandoCustomResource getCustomResource() {
        return entandoClusterInfrastructure;
    }

    @Override
    public final ServiceDeploymentResult createResult(Deployment deployment, Service service, Ingress ingress, Pod pod) {
        return new ServiceDeploymentResult(service, ingress);
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

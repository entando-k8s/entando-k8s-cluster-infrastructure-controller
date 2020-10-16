package org.entando.kubernetes.controller.clusterinfrastructure;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import org.entando.kubernetes.controller.ExposedDeploymentResult;

public class ClusterInfrastructureDeploymentResult  extends ExposedDeploymentResult<ClusterInfrastructureDeploymentResult> {

    public ClusterInfrastructureDeploymentResult(Pod pod, Service service,
            Ingress ingress) {
        super(pod, service, ingress);
    }
}

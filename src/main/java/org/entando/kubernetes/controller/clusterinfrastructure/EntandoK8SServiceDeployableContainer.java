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

import io.fabric8.kubernetes.api.model.EnvVar;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.entando.kubernetes.controller.EntandoOperatorConfig;
import org.entando.kubernetes.controller.EntandoOperatorConfigProperty;
import org.entando.kubernetes.controller.KeycloakClientConfig;
import org.entando.kubernetes.controller.KeycloakConnectionConfig;
import org.entando.kubernetes.controller.KubeUtils;
import org.entando.kubernetes.controller.database.DatabaseSchemaCreationResult;
import org.entando.kubernetes.controller.spi.ConfigurableResourceContainer;
import org.entando.kubernetes.controller.spi.DatabasePopulator;
import org.entando.kubernetes.controller.spi.KubernetesPermission;
import org.entando.kubernetes.controller.spi.ParameterizableContainer;
import org.entando.kubernetes.controller.spi.SpringBootDeployableContainer;
import org.entando.kubernetes.model.EntandoDeploymentSpec;
import org.entando.kubernetes.model.infrastructure.EntandoClusterInfrastructure;
import org.entando.kubernetes.model.plugin.ExpectedRole;

public class EntandoK8SServiceDeployableContainer implements SpringBootDeployableContainer, ParameterizableContainer,
        ConfigurableResourceContainer {

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
    public void addEnvironmentVariables(List<EnvVar> vars) {
        List<String> namespacesToObserve = EntandoOperatorConfig.getNamespacesToObserve();
        if (!namespacesToObserve.isEmpty()) {
            vars.add(new EnvVar(EntandoOperatorConfigProperty.ENTANDO_NAMESPACES_TO_OBSERVE.name(),
                    namespacesToObserve.stream().collect(Collectors.joining(",")), null));
        }
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
    public int getPrimaryPort() {
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

    @Override
    public EntandoDeploymentSpec getCustomResourceSpec() {
        return this.entandoClusterInfrastructure.getSpec();
    }
}

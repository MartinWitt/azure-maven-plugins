/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.resource;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.ResourceManager;
import com.azure.resourcemanager.resources.models.Deployment;
import com.azure.resourcemanager.resources.models.Deployments;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.telemetry.AzureTelemetry;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class ResourceDeploymentModule extends
    AbstractAzResourceModule<ResourceDeployment, ResourceGroup, Deployment> {

    public static final String NAME = "deployments";

    public ResourceDeploymentModule(@Nonnull ResourceGroup parent) {
        super(NAME, parent);
    }

    @Override
    public Deployments getClient() {
        return Optional.ofNullable(this.parent.getParent().getRemote()).map(ResourceManager::deployments).orElse(null);
    }

    @Override
    @AzureOperation(name = "resource.draft_for_create.resource|type", params = {"name", "this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    protected ResourceDeploymentDraft newDraftForCreate(@Nonnull String name, String resourceGroupName) {
        AzureTelemetry.getContext().setProperty("resourceType", this.getFullResourceType());
        AzureTelemetry.getContext().setProperty("subscriptionId", this.getSubscriptionId());
        return new ResourceDeploymentDraft(name, resourceGroupName, this);
    }

    @Override
    @AzureOperation(
        name = "resource.draft_for_update.resource|type",
        params = {"origin.getName()", "this.getResourceTypeName()"},
        type = AzureOperation.Type.SERVICE
    )
    protected ResourceDeploymentDraft newDraftForUpdate(@Nonnull ResourceDeployment origin) {
        AzureTelemetry.getContext().setProperty("resourceType", this.getFullResourceType());
        AzureTelemetry.getContext().setProperty("subscriptionId", this.getSubscriptionId());
        return new ResourceDeploymentDraft(origin);
    }

    @Nullable
    @Override
    @AzureOperation(name = "resource.load_resource.resource|type", params = {"name", "this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    protected Deployment loadResourceFromAzure(@Nonnull String name, String resourceGroup) {
        AzureTelemetry.getContext().setProperty("resourceType", this.getFullResourceType());
        AzureTelemetry.getContext().setProperty("subscriptionId", this.getSubscriptionId());
        try {
            return super.loadResourceFromAzure(name, resourceGroup);
        } catch (Exception e) {
            final Throwable cause = e instanceof ManagementException ? e : ExceptionUtils.getRootCause(e);
            if (cause instanceof ManagementException) {
                // SDK throws 403 instead of 404 when resource deployment doesn't exist.
                if (HttpStatus.SC_FORBIDDEN != ((ManagementException) cause).getResponse().getStatusCode()) {
                    throw e;
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    @AzureOperation(name = "resource.list_resources.type", params = {"this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    protected Stream<Deployment> loadResourcesFromAzure() {
        AzureTelemetry.getContext().setProperty("resourceType", this.getFullResourceType());
        AzureTelemetry.getContext().setProperty("subscriptionId", this.getSubscriptionId());
        final ResourceManager manager = Objects.requireNonNull(this.parent.getParent().getRemote());
        return manager.deployments().listByResourceGroup(this.parent.getName()).stream();
    }

    @Nonnull
    protected ResourceDeployment newResource(@Nonnull Deployment r) {
        return new ResourceDeployment(r, this);
    }

    @Override
    public String getResourceTypeName() {
        return "Resource deployment";
    }
}

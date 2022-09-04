/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.webapp;

import com.azure.resourcemanager.appservice.models.DeploymentSlots;
import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import com.azure.resourcemanager.appservice.models.WebSiteBase;
import com.microsoft.azure.toolkit.lib.appservice.AppServiceAppBase;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class WebAppDeploymentSlotModule extends AbstractAzResourceModule<WebAppDeploymentSlot, WebSiteBase> {

    public static final String NAME = "slots";

    public WebAppDeploymentSlotModule(@Nonnull WebApp parent) {
        super(NAME, parent);
    }

    @Override
    public DeploymentSlots getClient() {
        return Optional.of((WebApp) this.parent)
            .map(AppServiceAppBase::getFullRemote)
            .map(com.azure.resourcemanager.appservice.models.WebApp::deploymentSlots).orElse(null);
    }

    @Nonnull
    @Override
    @AzureOperation(name = "resource.draft_for_create.resource|type", params = {"name", "this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    protected WebAppDeploymentSlotDraft newDraftForCreate(@Nonnull String name, @Nullable String resourceGroupName) {
        return new WebAppDeploymentSlotDraft(name, this);
    }

    @Nonnull
    @Override
    @AzureOperation(
        name = "resource.draft_for_update.resource|type",
        params = {"origin.getName()", "this.getResourceTypeName()"},
        type = AzureOperation.Type.SERVICE
    )
    protected WebAppDeploymentSlotDraft newDraftForUpdate(@Nonnull WebAppDeploymentSlot origin) {
        return new WebAppDeploymentSlotDraft(origin);
    }

    @Nonnull
    @Override
    protected WebAppDeploymentSlot newResource(@Nonnull WebSiteBase remote) {
        return new WebAppDeploymentSlot((WebDeploymentSlotBasic) remote, this);
    }

    @Nonnull
    @Override
    protected WebAppDeploymentSlot newResource(@Nonnull String name, @Nullable String resourceGroupName) {
        return new WebAppDeploymentSlot(name, this);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Deployment slot";
    }
}

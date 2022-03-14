/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.appservice.webapp;

import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.microsoft.azure.toolkit.lib.common.model.AzResourceModule;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class WebAppDeploymentSlot extends WebAppBase<WebAppDeploymentSlot, WebApp, DeploymentSlot> {

    protected WebAppDeploymentSlot(@Nonnull String name, @Nonnull WebAppDeploymentSlotModule module) {
        super(name, module);
    }

    /**
     * copy constructor
     */
    protected WebAppDeploymentSlot(@Nonnull WebAppDeploymentSlot origin) {
        super(origin);
    }

    protected WebAppDeploymentSlot(@Nonnull DeploymentSlot remote, @Nonnull WebAppDeploymentSlotModule module) {
        super(remote.name(), module);
        this.setRemote(remote);
    }

    @Nonnull
    @Override
    public List<AzResourceModule<?, WebAppDeploymentSlot, ?>> getSubModules() {
        return Collections.emptyList();
    }
}

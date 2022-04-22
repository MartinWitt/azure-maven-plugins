/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.springcloud;

import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.appplatform.AppPlatformManager;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.AzureConfiguration;
import com.microsoft.azure.toolkit.lib.auth.Account;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzServiceSubscription;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzService;

import javax.annotation.Nonnull;
import java.util.Optional;

public final class AzureSpringCloud extends AbstractAzService<SpringCloudServiceSubscription, AppPlatformManager> {
    public AzureSpringCloud() {
        super("Microsoft.AppPlatform"); // for SPI
    }

    @Nonnull
    public SpringCloudClusterModule clusters(@Nonnull String subscriptionId) {
        final SpringCloudServiceSubscription rm = get(subscriptionId, null);
        assert rm != null;
        return rm.getClusterModule();
    }

    @Nonnull
    @Override
    protected AppPlatformManager loadResourceFromAzure(@Nonnull String subscriptionId, String resourceGroup) {
        final Account account = Azure.az(AzureAccount.class).account();
        final AzureConfiguration config = Azure.az().config();
        final String userAgent = config.getUserAgent();
        final HttpLogDetailLevel logLevel = Optional.ofNullable(config.getLogLevel()).map(HttpLogDetailLevel::valueOf).orElse(HttpLogDetailLevel.NONE);
        final AzureProfile azureProfile = new AzureProfile(null, subscriptionId, account.getEnvironment());
        return AppPlatformManager.configure()
            .withHttpClient(AbstractAzServiceSubscription.getDefaultHttpClient())
            .withLogLevel(logLevel)
            .withPolicy(AbstractAzServiceSubscription.getUserAgentPolicy(userAgent)) // set user agent with policy
            .authenticate(account.getTokenCredential(subscriptionId), azureProfile);
    }

    @Nonnull
    @Override
    protected SpringCloudServiceSubscription newResource(@Nonnull AppPlatformManager remote) {
        return new SpringCloudServiceSubscription(remote, this);
    }

    @Nonnull
    @Override
    public String getResourceTypeName() {
        return "Azure Spring Cloud";
    }
}

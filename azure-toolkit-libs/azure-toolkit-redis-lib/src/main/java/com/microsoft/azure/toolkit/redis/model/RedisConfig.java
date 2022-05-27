/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.redis.model;


import com.microsoft.azure.toolkit.lib.common.model.Region;
import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import com.microsoft.azure.toolkit.lib.resource.ResourceGroup;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @deprecated use {@link com.microsoft.azure.toolkit.redis.RedisCacheDraft} instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class RedisConfig {
    private String name;
    private String id;
    private ResourceGroup resourceGroup;
    private Subscription subscription;
    private Region region;
    private PricingTier pricingTier;
    private boolean enableNonSslPort;
}

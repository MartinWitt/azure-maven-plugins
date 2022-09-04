/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.cosmos.cassandra;

import com.azure.core.util.Context;
import com.azure.resourcemanager.cosmos.fluent.CosmosDBManagementClient;
import com.azure.resourcemanager.cosmos.fluent.models.CassandraKeyspaceGetResultsInner;
import com.azure.resourcemanager.cosmos.models.AutoscaleSettings;
import com.azure.resourcemanager.cosmos.models.CassandraKeyspaceCreateUpdateParameters;
import com.azure.resourcemanager.cosmos.models.CassandraKeyspaceResource;
import com.azure.resourcemanager.cosmos.models.CreateUpdateOptions;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.AzResource;
import com.microsoft.azure.toolkit.lib.cosmos.ICosmosDatabaseDraft;
import com.microsoft.azure.toolkit.lib.cosmos.model.DatabaseConfig;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public class CassandraKeyspaceDraft extends CassandraKeyspace implements
        ICosmosDatabaseDraft<CassandraKeyspace, CassandraKeyspaceGetResultsInner> {

    @Setter
    @Getter
    private DatabaseConfig config;

    protected CassandraKeyspaceDraft(@NotNull String name, @NotNull String resourceGroupName, @NotNull CassandraKeyspaceModule module) {
        super(name, resourceGroupName, module);
    }

    @Override
    public void reset() {

    }

    @NotNull
    @Override
    public CassandraKeyspaceGetResultsInner createResourceInAzure() {
        final CassandraCosmosDBAccount parent = (CassandraCosmosDBAccount) getParent();
        final CosmosDBManagementClient cosmosDBManagementClient = Objects.requireNonNull(parent.getRemote()).manager().serviceClient();
        final CassandraKeyspaceCreateUpdateParameters parameters = new CassandraKeyspaceCreateUpdateParameters()
                .withLocation(Objects.requireNonNull(parent.getRegion()).getName())
                .withResource(new CassandraKeyspaceResource().withId(this.getName()));
        final Integer throughput = ensureConfig().getThroughput();
        final Integer maxThroughput = ensureConfig().getMaxThroughput();
        assert ObjectUtils.anyNull(throughput, maxThroughput);
        if (ObjectUtils.anyNotNull(throughput, maxThroughput)) {
            final CreateUpdateOptions options = new CreateUpdateOptions();
            if (Objects.nonNull(ensureConfig().getThroughput())) {
                options.withThroughput(throughput);
            } else {
                options.withAutoscaleSettings(new AutoscaleSettings().withMaxThroughput(maxThroughput));
            }
            parameters.withOptions(options);
        }
        AzureMessager.getMessager().info(AzureString.format("Start creating keyspace({0})...", this.getName()));
        final CassandraKeyspaceGetResultsInner result = cosmosDBManagementClient.getCassandraResources().createUpdateCassandraKeyspace(this.getResourceGroupName(), parent.getName(),
                this.getName(), parameters, Context.NONE);
        AzureMessager.getMessager().success(AzureString.format("Keyspace({0}) is successfully created.", this.getName()));
        return result;
    }

    @NotNull
    @Override
    public CassandraKeyspaceGetResultsInner updateResourceInAzure(@NotNull CassandraKeyspaceGetResultsInner origin) {
        throw new UnsupportedOperationException("not support");
    }

    @Override
    public boolean isModified() {
        return config != null && ObjectUtils.anyNotNull(config.getThroughput(), config.getMaxThroughput(), config.getName());
    }

    @Nullable
    @Override
    public CassandraKeyspace getOrigin() {
        return null;
    }

    private DatabaseConfig ensureConfig() {
        this.config = Optional.ofNullable(config).orElseGet(DatabaseConfig::new);
        return this.config;
    }

}

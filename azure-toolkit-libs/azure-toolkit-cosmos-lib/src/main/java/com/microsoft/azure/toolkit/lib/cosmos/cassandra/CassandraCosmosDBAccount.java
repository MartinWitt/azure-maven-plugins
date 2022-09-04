/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.cosmos.cassandra;

import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.cosmos.CosmosDBAccount;
import com.microsoft.azure.toolkit.lib.cosmos.CosmosDBAccountModule;
import com.microsoft.azure.toolkit.lib.cosmos.model.CassandraDatabaseAccountConnectionString;
import com.microsoft.azure.toolkit.lib.cosmos.model.CosmosDBAccountConnectionString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CassandraCosmosDBAccount extends CosmosDBAccount {
    private final CassandraKeyspaceModule keyspaceModule;

    public CassandraCosmosDBAccount(@Nonnull String name, @Nonnull String resourceGroupName, @Nonnull CosmosDBAccountModule module) {
        super(name, resourceGroupName, module);
        this.keyspaceModule = new CassandraKeyspaceModule(this);
    }

    public CassandraCosmosDBAccount(@Nonnull CosmosDBAccount account) {
        super(account);
        this.keyspaceModule = new CassandraKeyspaceModule(this);
    }

    public CassandraCosmosDBAccount(@Nonnull com.azure.resourcemanager.cosmos.models.CosmosDBAccount remote, @Nonnull CosmosDBAccountModule module) {
        super(remote.name(), ResourceId.fromString(remote.id()).resourceGroupName(), module);
        this.keyspaceModule = new CassandraKeyspaceModule(this);
    }

    public CassandraKeyspaceModule keySpaces() {
        return this.keyspaceModule;
    }

    @Nullable
    public String getPrimaryConnectionString() {
        return listConnectionStrings().getPrimaryConnectionString();
    }

    @Nonnull
    public CassandraDatabaseAccountConnectionString getCassandraConnectionString() {
        return Optional.ofNullable(getPrimaryConnectionString())
                .map(CassandraDatabaseAccountConnectionString::fromConnectionString)
                .orElseGet(CassandraDatabaseAccountConnectionString::new);
    }

    @Override
    public @Nonnull List<AbstractAzResourceModule<?, ?>> getSubModules() {
        return Collections.singletonList(this.keyspaceModule);
    }

    @Nullable
    public Integer getPort() {
        return getCassandraConnectionString().getPort();
    }

    @Nullable
    public String getUserName() {
        return getCassandraConnectionString().getUsername();
    }

    @Nullable
    public String getContactPoint() {
        return getCassandraConnectionString().getContactPoint();
    }

    @Nonnull
    @Override
    public CosmosDBAccountConnectionString getCosmosDBAccountPrimaryConnectionString() {
        return getCassandraConnectionString();
    }
}

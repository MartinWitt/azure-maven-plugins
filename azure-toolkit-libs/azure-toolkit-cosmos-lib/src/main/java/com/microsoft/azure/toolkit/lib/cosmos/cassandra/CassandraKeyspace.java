/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.cosmos.cassandra;

import com.azure.resourcemanager.cosmos.fluent.models.CassandraKeyspaceGetResultsInner;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import com.microsoft.azure.toolkit.lib.common.model.Deletable;
import com.microsoft.azure.toolkit.lib.cosmos.CosmosDBAccount;
import com.microsoft.azure.toolkit.lib.cosmos.ICosmosCollection;
import com.microsoft.azure.toolkit.lib.cosmos.ICosmosDatabase;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

public class CassandraKeyspace extends AbstractAzResource<CassandraKeyspace, CassandraKeyspaceGetResultsInner> implements Deletable, ICosmosDatabase {

    private final CassandraTableModule containerModule;

    protected CassandraKeyspace(@NotNull String name, @NotNull String resourceGroupName, @NotNull CassandraKeyspaceModule module) {
        super(name, resourceGroupName, module);
        this.containerModule = new CassandraTableModule(this);
    }

    protected CassandraKeyspace(@Nonnull CassandraKeyspace account) {
        super(account);
        this.containerModule = new CassandraTableModule(this);
    }

    protected CassandraKeyspace(@Nonnull CassandraKeyspaceGetResultsInner remote, @Nonnull CassandraKeyspaceModule module) {
        super(remote.name(), module);
        this.containerModule = new CassandraTableModule(this);
    }

    @NotNull
    @Override
    public List<AbstractAzResourceModule<?, ?>> getSubModules() {
        return Collections.singletonList(containerModule);
    }

    public CassandraTableModule tables() {
        return this.containerModule;
    }

    @NotNull
    @Override
    public String loadStatus(@NotNull CassandraKeyspaceGetResultsInner remote) {
        return Status.RUNNING;
    }

    @Override
    public List<? extends ICosmosCollection> listCollection() {
        return tables().list();
    }
}

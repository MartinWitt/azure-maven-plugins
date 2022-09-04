/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.toolkit.lib.cosmos.cassandra;

import com.azure.resourcemanager.cosmos.fluent.CassandraResourcesClient;
import com.azure.resourcemanager.cosmos.fluent.models.CassandraTableGetResultsInner;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class CassandraTableModule extends AbstractAzResourceModule<CassandraTable, CassandraTableGetResultsInner> {
    private static final String NAME = "tables";

    public CassandraTableModule(@NotNull CassandraKeyspace parent) {
        super(NAME, parent);
    }

    @NotNull
    @Override
    protected CassandraTable newResource(@NotNull CassandraTableGetResultsInner remote) {
        return new CassandraTable(remote, this);
    }

    @NotNull
    @Override
    protected CassandraTable newResource(@NotNull String name, @Nullable String resourceGroupName) {
        return new CassandraTable(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @NotNull
    @Override
    protected Stream<CassandraTableGetResultsInner> loadResourcesFromAzure() {
        return Optional.ofNullable(getClient()).map(client ->
                client.listCassandraTables(parent.getResourceGroupName(), parent.getParent().getName(), parent.getName()).stream()).orElse(Stream.empty());
    }

    @Nullable
    @Override
    protected CassandraTableGetResultsInner loadResourceFromAzure(@NotNull String name, @Nullable String resourceGroup) {
        return Optional.ofNullable(getClient()).map(client -> client.getCassandraTable(parent.getResourceGroupName(), parent.getParent().getName(), parent.getName(), name)).orElse(null);
    }

    @Override
    protected void deleteResourceFromAzure(@NotNull String resourceId) {
        final ResourceId id = ResourceId.fromString(resourceId);
        Optional.ofNullable(getClient()).ifPresent(client -> client.deleteCassandraTable(id.resourceGroupName(), id.parent().parent().name(), id.parent().name(), id.name()));
    }

    @Override
    @Nullable
    protected CassandraResourcesClient getClient() {
        return ((CassandraKeyspaceModule)this.getParent().getModule()).getClient();
    }
}

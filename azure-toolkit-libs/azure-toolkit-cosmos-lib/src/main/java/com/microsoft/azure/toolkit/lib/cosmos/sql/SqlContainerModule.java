/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */
package com.microsoft.azure.toolkit.lib.cosmos.sql;

import com.azure.resourcemanager.cosmos.fluent.SqlResourcesClient;
import com.azure.resourcemanager.cosmos.fluent.models.SqlContainerGetResultsInner;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class SqlContainerModule extends AbstractAzResourceModule<SqlContainer, SqlContainerGetResultsInner> {
    private static final String NAME = "containers";

    public SqlContainerModule(@NotNull SqlDatabase parent) {
        super(NAME, parent);
    }

    @NotNull
    @Override
    protected SqlContainer newResource(@NotNull SqlContainerGetResultsInner sqlContainer) {
        return new SqlContainer(sqlContainer, this);
    }

    @NotNull
    @Override
    protected SqlContainer newResource(@NotNull String name, @Nullable String resourceGroupName) {
        return new SqlContainer(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @NotNull
    @Override
    protected Stream<SqlContainerGetResultsInner> loadResourcesFromAzure() {
        return Optional.ofNullable(getClient()).map(client ->
                client.listSqlContainers(parent.getResourceGroupName(), parent.getParent().getName(), parent.getName()).stream()).orElse(Stream.empty());
    }

    @Nullable
    @Override
    protected SqlContainerGetResultsInner loadResourceFromAzure(@NotNull String name, @Nullable String resourceGroup) {
        return Optional.ofNullable(getClient()).map(client -> client.getSqlContainer(parent.getResourceGroupName(), parent.getParent().getName(), parent.getName(), name)).orElse(null);
    }

    @Override
    protected void deleteResourceFromAzure(@NotNull String resourceId) {
        final ResourceId id = ResourceId.fromString(resourceId);
        Optional.ofNullable(getClient()).ifPresent(client -> client.deleteSqlContainer(id.resourceGroupName(), id.parent().parent().name(), id.parent().name(), id.name()));
    }

    @Override
    protected @Nullable SqlResourcesClient getClient() {
        return ((SqlDatabaseModule) this.parent.getModule()).getClient();
    }
}

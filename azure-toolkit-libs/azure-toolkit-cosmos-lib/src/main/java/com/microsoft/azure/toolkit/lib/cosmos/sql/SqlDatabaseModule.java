/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.cosmos.sql;

import com.azure.resourcemanager.cosmos.fluent.SqlResourcesClient;
import com.azure.resourcemanager.cosmos.fluent.models.SqlDatabaseGetResultsInner;
import com.azure.resourcemanager.cosmos.models.CosmosDBAccount;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResourceModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class SqlDatabaseModule extends AbstractAzResourceModule<SqlDatabase, SqlDatabaseGetResultsInner> {
    private static final String NAME = "sqlDatabases";

    public SqlDatabaseModule(@NotNull SqlCosmosDBAccount parent) {
        super(NAME, parent);
    }

    @NotNull
    @Override
    protected SqlDatabase newResource(@NotNull SqlDatabaseGetResultsInner resource) {
        return new SqlDatabase(resource, this);
    }

    @NotNull
    @Override
    protected SqlDatabase newResource(@NotNull String name, @Nullable String resourceGroupName) {
        return new SqlDatabase(name, Objects.requireNonNull(resourceGroupName), this);
    }

    @NotNull
    @Override
    protected Stream<SqlDatabaseGetResultsInner> loadResourcesFromAzure() {
        return Optional.ofNullable(getClient()).map(client ->
                client.listSqlDatabases(parent.getResourceGroupName(), parent.getName()).stream()).orElse(Stream.empty());
    }

    @Nullable
    @Override
    protected SqlDatabaseGetResultsInner loadResourceFromAzure(@NotNull String name, @Nullable String resourceGroup) {
        return Optional.ofNullable(getClient()).map(client -> client.getSqlDatabase(parent.getResourceGroupName(), parent.getName(), name)).orElse(null);
    }

    @Override
    protected void deleteResourceFromAzure(@NotNull String resourceId) {
        final ResourceId id = ResourceId.fromString(resourceId);
        Optional.ofNullable(getClient()).ifPresent(client -> client.deleteSqlDatabase(id.resourceGroupName(), id.parent().name(), id.name()));
    }

    @NotNull
    @Override
    protected SqlDatabaseDraft newDraftForCreate(@NotNull String name, @Nullable String rgName) {
        return new SqlDatabaseDraft(name, Objects.requireNonNull(rgName), this);
    }

    @NotNull
    @Override
    protected SqlDatabaseDraft newDraftForUpdate(@NotNull SqlDatabase sqlDatabase) {
        throw new UnsupportedOperationException("not support");
    }

    @Nullable
    @Override
    protected SqlResourcesClient getClient() {
        return Optional.ofNullable(this.parent.getRemote())
            .map(r -> ((CosmosDBAccount) r))
            .map(account -> account.manager().serviceClient().getSqlResources()).orElse(null);
    }
}

/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.operation;

import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.Callable;

public interface Operation {
    String UNKNOWN_NAME = "<unknown>.<unknown>";

    @Nonnull
    String getExecutionId();

    @Nonnull
    default String getId() {
        return Optional.ofNullable(this.getTitle()).map(AzureString::getName).orElse(UNKNOWN_NAME);
    }

    Callable<?> getBody();

    @Nonnull
    String getType();

    @Nullable
    AzureString getTitle();

    void setParent(Operation operation);

    @Nullable
    Operation getParent();

    OperationContext getContext();

    default Operation getEffectiveParent() {
        final Operation parent = this.getParent();
        if (parent == null) {
            return null;
        } else if (!parent.getId().equals(UNKNOWN_NAME)) {
            return parent;
        } else {
            return parent.getEffectiveParent();
        }
    }

    @Nullable
    default Operation getActionParent() {
        if (this.getType().equals(AzureOperation.Type.ACTION.name())) {
            return this;
        }
        return Optional.ofNullable(this.getParent()).map(Operation::getActionParent).orElse(null);
    }

    @Nullable
    static Operation current() {
        return OperationThreadContext.current().currentOperation();
    }
}

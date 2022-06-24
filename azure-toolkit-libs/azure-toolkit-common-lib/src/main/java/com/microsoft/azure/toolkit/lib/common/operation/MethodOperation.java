/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.operation;

import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.utils.aspect.ExpressionUtils;
import com.microsoft.azure.toolkit.lib.common.utils.aspect.MethodInvocation;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.concurrent.Callable;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class MethodOperation extends OperationBase {

    @EqualsAndHashCode.Include
    private final MethodInvocation invocation;

    @Override
    public String toString() {
        final AzureOperation annotation = this.invocation.getAnnotation(AzureOperation.class);
        return String.format("{name:'%s', method:%s}", annotation.name(), this.invocation.getMethod().getName());
    }

    @Nonnull
    public String getId() {
        final AzureOperation annotation = this.invocation.getAnnotation(AzureOperation.class);
        return annotation.name();
    }

    @Override
    public Callable<Object> getBody() {
        return this.invocation::invoke;
    }

    @Nonnull
    public String getType() {
        final AzureOperation annotation = this.invocation.getAnnotation(AzureOperation.class);
        return annotation.type().name();
    }

    public AzureString getDescription() {
        final AzureOperation annotation = this.invocation.getAnnotation(AzureOperation.class);
        final String name = annotation.name();
        final String[] params = Arrays.stream(annotation.params()).map(e -> ExpressionUtils.interpret(e, this.invocation)).toArray(String[]::new);
        return OperationBundle.description(name, (Object[]) params);
    }
}

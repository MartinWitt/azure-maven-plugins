/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.task;

import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.operation.OperationBase;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.Callable;

@Getter
@Setter
public class AzureTask<T> extends OperationBase {
    @Nonnull
    private final Modality modality;
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Callable<T> body;
    @Nullable
    private final Object project;
    private final boolean cancellable;
    @Nullable
    private final AzureString description;
    @Builder.Default
    private boolean backgroundable = true;
    @Nullable
    private Boolean backgrounded = null;
    @Nonnull
    @Builder.Default
    private String type = "ASYNC";
    private Monitor monitor;

    public AzureTask() {
        this((Callable<T>) null);
    }

    public AzureTask(@Nonnull Runnable runnable) {
        this(runnable, Modality.DEFAULT);
    }

    public AzureTask(@Nonnull String title, @Nonnull Runnable runnable) {
        this(title, runnable, Modality.DEFAULT);
    }

    public AzureTask(@Nonnull AzureString title, @Nonnull Runnable runnable) {
        this(title, runnable, Modality.DEFAULT);
    }

    public AzureTask(@Nullable Callable<T> body) {
        this(body, Modality.DEFAULT);
    }

    public AzureTask(@Nonnull String title, @Nonnull Callable<T> body) {
        this(null, title, false, body, Modality.DEFAULT);
    }

    public AzureTask(@Nonnull AzureString title, @Nonnull Callable<T> body) {
        this(null, title, false, body, Modality.DEFAULT);
    }

    public AzureTask(@Nonnull Runnable runnable, @Nonnull Modality modality) {
        this(null, (String) null, false, runnable, modality);
    }

    public AzureTask(@Nonnull String title, @Nonnull Runnable runnable, @Nonnull Modality modality) {
        this(null, title, false, runnable, modality);
    }

    public AzureTask(@Nonnull AzureString title, @Nonnull Runnable runnable, @Nonnull Modality modality) {
        this(null, title, false, runnable, modality);
    }

    public AzureTask(@Nullable Callable<T> body, @Nonnull Modality modality) {
        this(null, (String) null, false, body, modality);
    }

    public AzureTask(@Nonnull String title, @Nonnull Callable<T> body, @Nonnull Modality modality) {
        this(null, title, false, body, modality);
    }

    public AzureTask(@Nonnull AzureString title, @Nonnull Callable<T> body, @Nonnull Modality modality) {
        this(null, title, false, body, modality);
    }

    public AzureTask(@Nullable Object project, @Nonnull String title, boolean cancellable, @Nonnull Runnable runnable) {
        this(project, title, cancellable, runnable, Modality.DEFAULT);
    }

    public AzureTask(@Nullable Object project, @Nonnull AzureString title, boolean cancellable, @Nonnull Runnable runnable) {
        this(project, title, cancellable, runnable, Modality.DEFAULT);
    }

    public AzureTask(@Nullable Object project, @Nonnull String title, boolean cancellable, @Nonnull Callable<T> body) {
        this(project, title, cancellable, body, Modality.DEFAULT);
    }

    public AzureTask(@Nullable Object project, @Nonnull AzureString title, boolean cancellable, @Nonnull Callable<T> body) {
        this(project, title, cancellable, body, Modality.DEFAULT);
    }

    public AzureTask(@Nullable Object project, @Nullable String title, boolean cancellable, @Nonnull Runnable runnable, @Nonnull Modality modality) {
        this(project, Optional.ofNullable(title).map(AzureString::fromString).orElse(null), cancellable, runnable, modality);
    }

    public AzureTask(@Nullable Object project, @Nullable AzureString title, boolean cancellable, @Nonnull Runnable runnable, @Nonnull Modality modality) {
        this(project, title, cancellable, () -> {
            runnable.run();
            return null;
        }, modality);
    }

    public AzureTask(@Nullable Object project, @Nullable String title, boolean cancellable, @Nullable Callable<T> body, @Nonnull Modality modality) {
        this(project, Optional.ofNullable(title).map(AzureString::fromString).orElse(null), cancellable, body, modality);
    }

    public AzureTask(@Nullable Object project, @Nullable AzureString title, boolean cancellable, @Nullable Callable<T> body, @Nonnull Modality modality) {
        this.project = project;
        this.description = title;
        this.cancellable = cancellable;
        this.monitor = new DefaultMonitor();
        this.body = body;
        this.modality = modality;
    }

    @Nonnull
    @Override
    public String getId() {
        return Optional.ofNullable(this.getDescription()).map(AzureString::getName).orElse(UNKNOWN_NAME);
    }

    @Nonnull
    public String getExecutionId() {
        return "&" + super.getExecutionId();
    }

    @Override
    public String toString() {
        return String.format("{name:'%s'}", this.getId());
    }

    @Nonnull
    public Callable<T> getBody() {
        return Optional.ofNullable(this.body).orElse(this::doExecute);
    }

    @SneakyThrows
    public final T execute() {
        return this.getBody().call();
    }

    protected T doExecute() throws Exception {
        throw new UnsupportedOperationException();
    }

    public enum Modality {
        DEFAULT, ANY, NONE
    }

    public interface Monitor {
        void cancel();

        boolean isCancelled();
    }

    public static class DefaultMonitor implements Monitor {
        private boolean cancelled = false;

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }
    }
}

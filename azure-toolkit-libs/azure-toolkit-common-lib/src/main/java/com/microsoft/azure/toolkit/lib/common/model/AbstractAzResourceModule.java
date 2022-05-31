/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.common.model;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.resources.fluentcore.arm.ResourceId;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsGettingById;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsGettingByName;
import com.azure.resourcemanager.resources.fluentcore.arm.collection.SupportsGettingByResourceGroup;
import com.azure.resourcemanager.resources.fluentcore.collection.SupportsDeletingById;
import com.azure.resourcemanager.resources.fluentcore.collection.SupportsListing;
import com.google.common.collect.Sets;
import com.microsoft.azure.toolkit.lib.AzService;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.account.IAzureAccount;
import com.microsoft.azure.toolkit.lib.common.event.AzureEventBus;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.common.utils.Debouncer;
import com.microsoft.azure.toolkit.lib.common.utils.TailingDebouncer;
import com.microsoft.azure.toolkit.lib.resource.GenericResource;
import com.microsoft.azure.toolkit.lib.resource.GenericResourceModule;
import com.microsoft.azure.toolkit.lib.resource.ResourceGroup;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.microsoft.azure.toolkit.lib.common.model.AzResource.RESOURCE_GROUP_PLACEHOLDER;

@Slf4j
@Getter
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AbstractAzResourceModule<T extends AbstractAzResource<T, P, R>, P extends AbstractAzResource<P, ?, ?>, R>
    implements AzResourceModule<T, P, R> {
    @Nonnull
    @ToString.Include
    @EqualsAndHashCode.Include
    private final String name;
    @Nonnull
    @EqualsAndHashCode.Include
    protected final P parent;
    @Nonnull
    @ToString.Include
    @Getter(AccessLevel.NONE)
    private final AtomicLong syncTimeRef = new AtomicLong(-1);
    @Nonnull
    @Getter(AccessLevel.NONE)
    private final Map<String, Optional<T>> resources = new CaseInsensitiveMap<>();
    @Nonnull
    @Getter(AccessLevel.NONE)
    private final Debouncer fireEvents = new TailingDebouncer(this::fireChildrenChangedEvent, 300);

    @Override
    @AzureOperation(name = "resource.refresh.type", params = {"this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    public void refresh() {
        log.debug("[{}]:refresh()", this.name);
        this.syncTimeRef.set(-1);
        AzureEventBus.emit("module.refreshed.module", this);
    }

    @Nonnull
    @Override
    public List<T> list() { // getResources
        log.debug("[{}]:list()", this.name);
        if (this.parent.isDraftForCreating()) {
            log.debug("[{}]:list->parent.isDraftForCreating()=true", this.name);
            return Collections.emptyList();
        }
        Azure.az(IAzureAccount.class).account();
        synchronized (this.syncTimeRef) {
            while (this.syncTimeRef.get() == 0) {
                try {
                    this.syncTimeRef.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    this.syncTimeRef.set(-1);
                }
            }
            if (this.syncTimeRef.compareAndSet(-1, 0)) {
                log.debug("[{}]:list->this.reload()", this.name);
                this.reloadResources();
            }
            log.debug("[{}]:list->this.resources.values()", this.name);
            return this.resources.values().stream().filter(Optional::isPresent).map(Optional::get)
                .sorted(Comparator.comparing(AbstractAzResource::getName)).collect(Collectors.toList());
        }
    }

    private void reloadResources() {
        log.debug("[{}]:reload()", this.name);
        Stream<R> loaded;
        try {
            log.debug("[{}]:reload->loadResourcesFromAzure()", this.name);
            loaded = this.loadResourcesFromAzure();
        } catch (Throwable t) {
            log.debug("[{}]:reload->loadResourcesFromAzure()=EXCEPTION", this.name, t);
            synchronized (this.syncTimeRef) {
                this.syncTimeRef.compareAndSet(0, -1);
                this.syncTimeRef.notifyAll();
            }
            AzureMessager.getMessager().error(t);
            return;
        }
        final Map<String, T> loadedResources = loaded.map(this::newResource).collect(Collectors.toMap(AbstractAzResource::getName, r -> r));
        this.setResources(loadedResources);
    }

    private void setResources(Map<String, T> loadedResources) {
        synchronized (this.syncTimeRef) {
            final Set<String> localResources = this.resources.values().stream().filter(Optional::isPresent).map(Optional::get)
                .map(AbstractAzResource::getName).collect(Collectors.toSet());
            final Set<String> creating = this.resources.values().stream().filter(Optional::isPresent).map(Optional::get)
                .filter(r -> AzResource.Status.CREATING.equals(r.getStatus())).map(AbstractAzResource::getName).collect(Collectors.toSet());
            log.debug("[{}]:reload().creating={}", this.name, creating);
            final Sets.SetView<String> refreshed = Sets.intersection(localResources, loadedResources.keySet());
            log.debug("[{}]:reload().refreshed={}", this.name, refreshed);
            final Sets.SetView<String> deleted = Sets.difference(Sets.difference(localResources, loadedResources.keySet()), creating);
            log.debug("[{}]:reload().deleted={}", this.name, deleted);
            final Sets.SetView<String> added = Sets.difference(loadedResources.keySet(), localResources);
            log.debug("[{}]:reload().added={}", this.name, added);

            log.debug("[{}]:reload.refreshed->resource.setRemote", this.name);
            refreshed.forEach(name -> this.resources.get(name).ifPresent(r -> r.setRemote(loadedResources.get(name).getRemote())));
            log.debug("[{}]:reload.deleted->deleteResourceFromLocal", this.name);
            deleted.forEach(name -> Optional.ofNullable(this.deleteResourceFromLocal(name, true)).ifPresent(t -> t.setStatus(AzResource.Status.DELETED)));
            log.debug("[{}]:reload.added->addResourceToLocal", this.name);
            added.forEach(name -> this.addResourceToLocal(name, loadedResources.get(name), true));
            this.syncTimeRef.set(System.currentTimeMillis());
            this.syncTimeRef.notifyAll();
        }
    }

    @Nonnull
    public List<T> listByResourceGroup(@Nonnull String resourceGroup) {
        log.debug("[{}]:listByResourceGroupName({})", this.name, resourceGroup);
        return this.list().stream().filter(r -> r.getResourceGroupName().equalsIgnoreCase(resourceGroup)).collect(Collectors.toList());
    }

    public void clear() {
        log.debug("[{}]:clear()", this.name);
        synchronized (this.syncTimeRef) {
            this.resources.clear();
            this.syncTimeRef.set(-1);
            this.syncTimeRef.notifyAll();
        }
    }

    @Nullable
    @Override
    public T get(@Nullable String name, @Nullable String resourceGroup) {
        log.debug("[{}]:get({}, {})", this.name, name, resourceGroup);
        if (StringUtils.isBlank(name) || this.parent.isDraftForCreating()) {
            log.debug("[{}]:get->parent.isDraftForCreating()=true||isBlank(name)=true", this.name);
            return null;
        }
        Azure.az(IAzureAccount.class).account();
        if (!this.resources.containsKey(name)) {
            R remote = null;
            try {
                log.debug("[{}]:get({}, {})->loadResourceFromAzure()", this.name, name, resourceGroup);
                remote = loadResourceFromAzure(name, resourceGroup);
            } catch (Exception e) {
                log.debug("[{}]:get({}, {})->loadResourceFromAzure()=EXCEPTION", this.name, name, resourceGroup, e);
                final Throwable cause = e instanceof ManagementException ? e : ExceptionUtils.getRootCause(e);
                if (cause instanceof ManagementException) {
                    if (HttpStatus.SC_NOT_FOUND != ((ManagementException) cause).getResponse().getStatusCode()) {
                        log.debug("[{}]:get({}, {})->loadResourceFromAzure()=SC_NOT_FOUND", this.name, name, resourceGroup, e);
                        throw e;
                    }
                }
            }
            if (Objects.isNull(remote)) {
                log.debug("[{}]:get({}, {})->addResourceToLocal({}, null)", this.name, name, resourceGroup, name);
                this.addResourceToLocal(name, null, true);
            } else {
                final T resource = newResource(remote);
                log.debug("[{}]:get({}, {})->addResourceToLocal({}, resource)", this.name, name, resourceGroup, name);
                this.addResourceToLocal(name, resource, true);
            }
        }
        log.debug("[{}]:get({}, {})->this.resources.get({})", this.name, name, resourceGroup, name);
        return this.resources.get(name).orElse(null);
    }

    @Nullable
    public T get(@Nonnull String resourceId) {
        final ResourceId id = ResourceId.fromString(resourceId);
        log.debug("[{}]:get({})", this.name, resourceId);
        return this.get(id.name(), id.resourceGroupName());
    }

    @Override
    public boolean exists(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:exists({}, {})", this.name, name, resourceGroup);
        final T resource = this.get(name, resourceGroup);
        return Objects.nonNull(resource) && resource.exists();
    }

    @Override
    public void delete(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:delete({}, {})", this.name, name, resourceGroup);
        log.debug("[{}]:delete->this.get({}, {})", this.name, name, resourceGroup);
        final T resource = this.get(name, resourceGroup);
        if (Objects.nonNull(resource)) {
            log.debug("[{}]:delete->resource.delete()", this.name);
            resource.delete();
        } else {
            throw new AzureToolkitRuntimeException(String.format("resource \"%s\" doesn't exist", name));
        }
    }

    @Nonnull
    public T getOrDraft(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:getOrDraft({}, {})", this.name, name, resourceGroup);
        return Optional.ofNullable(this.get(name, resourceGroup)).orElseGet(() -> this.cast(this.newDraftForCreate(name, resourceGroup)));
    }

    @Nonnull
    public T getOrInit(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:getOrDraft({}, {})", this.name, name, resourceGroup);
        synchronized (this.syncTimeRef) {
            return this.resources.getOrDefault(name, Optional.empty()).orElseGet(() -> {
                final T resource = this.newResource(name, resourceGroup);
                log.debug("[{}]:get({}, {})->addResourceToLocal({}, resource)", this.name, name, resourceGroup, name);
                this.addResourceToLocal(name, resource);
                return resource;
            });
        }
    }

    @Nonnull
    public <D extends AzResource.Draft<T, R>> D updateOrCreate(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:updateOrCreate({}, {})", this.name, name, resourceGroup);
        final T resource = this.get(name, resourceGroup);
        if (Objects.nonNull(resource)) {
            return this.cast(this.newDraftForUpdate(resource));
        }
        return this.cast(this.newDraftForCreate(name, resourceGroup));
    }

    @Nonnull
    public <D extends AzResource.Draft<T, R>> D create(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:create({}, {})", this.name, name, resourceGroup);
        // TODO: use generics to avoid class casting
        log.debug("[{}]:create->newDraftForCreate({}, {})", this.name, name, resourceGroup);
        return this.cast(this.newDraftForCreate(name, resourceGroup));
    }

    @Nonnull
    @Override
    public T create(@Nonnull AzResource.Draft<T, R> draft) {
        log.debug("[{}]:create(draft:{})", this.name, draft);
        final T existing = this.get(draft.getName(), draft.getResourceGroupName());
        if (Objects.isNull(existing)) {
            final T resource = cast(draft);
            // this will notify azure explorer to show a draft resource first
            log.debug("[{}]:create->addResourceToLocal({})", this.name, resource);
            this.addResourceToLocal(resource.getName(), resource);
            final ResourceId id = ResourceId.fromString(resource.getId());
            final ResourceGroup resourceGroup = resource.getResourceGroup();
            if (Objects.isNull(id.parent()) && Objects.nonNull(resourceGroup) && !(resource instanceof ResourceGroup)) {
                final GenericResourceModule genericResourceModule = resourceGroup.genericResources();
                final GenericResource genericResource = genericResourceModule.newResource(resource);
                //noinspection unchecked,rawtypes
                ((AbstractAzResourceModule) genericResourceModule).addResourceToLocal(resource.getId(), genericResource);
            }
            log.debug("[{}]:create->doModify(draft.createResourceInAzure({}))", this.name, resource);
            try {
                resource.doModify(draft::createResourceInAzure, AzResource.Status.CREATING);
            } catch (RuntimeException e) {
                resource.delete();
                throw e;
            }
            return resource;
        }
        throw new AzureToolkitRuntimeException(String.format("resource \"%s\" is existing", existing.getName()));
    }

    @Nonnull
    <D extends AzResource.Draft<T, R>> D update(@Nonnull T resource) {
        log.debug("[{}]:update(resource:{})", this.name, resource);
        if (resource.isDraftForCreating()) {
            log.warn("[{}]:updating(resource:{}) from a draftForCreating", this.name, resource);
        }
        if (resource.isDraftForUpdating()) {
            return this.cast(resource);
        }
        log.debug("[{}]:update->newDraftForUpdate({})", this.name, resource);
        final T draft = this.cast(this.newDraftForUpdate(resource));
        return this.cast(draft);
    }

    @Nonnull
    @Override
    public T update(@Nonnull AzResource.Draft<T, R> draft) {
        log.debug("[{}]:update(draft:{})", this.name, draft);
        final T resource = this.get(draft.getName(), draft.getResourceGroupName());
        if (Objects.nonNull(resource) && Objects.nonNull(resource.getRemote())) {
            log.debug("[{}]:update->doModify(draft.updateResourceInAzure({}))", this.name, resource.getRemote());
            resource.doModify(() -> draft.updateResourceInAzure(resource.getRemote()), AzResource.Status.UPDATING);
            return resource;
        }
        throw new AzureToolkitRuntimeException(String.format("resource \"%s\" doesn't exist", draft.getName()));
    }

    @Nonnull
    public String toResourceId(@Nonnull String resourceName, @Nullable String resourceGroup) {
        resourceGroup = StringUtils.firstNonBlank(resourceGroup, this.getParent().getResourceGroupName(), RESOURCE_GROUP_PLACEHOLDER);
        return String.format("%s/%s/%s", this.parent.getId(), this.getName(), resourceName).replace(RESOURCE_GROUP_PLACEHOLDER, resourceGroup);
    }

    @Nullable
    T deleteResourceFromLocal(@Nonnull String name, boolean... silent) {
        log.debug("[{}]:deleteResourceFromLocal({})", this.name, name);
        log.debug("[{}]:deleteResourceFromLocal->this.resources.remove({})", this.name, name);
        synchronized (this.syncTimeRef) {
            final Optional<T> removed = this.resources.remove(name);
            if (Objects.nonNull(removed) && removed.isPresent() && (silent.length == 0 || !silent[0])) {
                log.debug("[{}]:deleteResourceFromLocal->fireResourcesChangedEvent()", this.name);
                fireEvents.debounce();
            }
            return Objects.nonNull(removed) ? removed.orElse(null) : null;
        }
    }

    private void addResourceToLocal(@Nonnull String name, @Nullable T resource, boolean... silent) {
        log.debug("[{}]:addResourceToLocal({}, {})", this.name, name, resource);
        synchronized (this.syncTimeRef) {
            final Optional<T> oldResource = this.resources.getOrDefault(name, Optional.empty());
            final Optional<T> newResource = Optional.ofNullable(resource);
            if (!oldResource.isPresent()) {
                log.debug("[{}]:addResourceToLocal->this.resources.put({}, {})", this.name, name, resource);
                this.resources.put(name, newResource);
                if (newResource.isPresent() && (silent.length == 0 || !silent[0])) {
                    log.debug("[{}]:addResourceToLocal->fireResourcesChangedEvent()", this.name);
                    fireEvents.debounce();
                }
            }
        }
    }

    private void fireChildrenChangedEvent() {
        log.debug("[{}]:fireChildrenChangedEvent()", this.name);
        if (this.getParent() instanceof AbstractAzServiceSubscription) {
            final AzResourceModule<P, ?, ?> service = this.getParent().getModule();
            AzureEventBus.emit("service.children_changed.service", service);
        }
        if (this instanceof AzService) {
            AzureEventBus.emit("service.children_changed.service", this);
        }
        AzureEventBus.emit("resource.children_changed.resource", this.getParent());
        AzureEventBus.emit("module.children_changed.module", this);
    }

    @Nonnull
    @AzureOperation(name = "resource.list_resources.type", params = {"this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    protected Stream<R> loadResourcesFromAzure() {
        log.debug("[{}]:loadResourcesFromAzure()", this.getName());
        final Object client = this.getClient();
        if (client instanceof SupportsListing) {
            log.debug("[{}]:loadResourcesFromAzure->client.list()", this.name);
            return this.<SupportsListing<R>>cast(client).list().stream();
        } else if (client != null) {
            log.debug("[{}]:loadResourcesFromAzure->NOT Supported", this.name);
            throw new AzureToolkitRuntimeException("not supported");
        }
        return Stream.empty();
    }

    @Nullable
    @AzureOperation(name = "resource.load_resource.resource|type", params = {"name", "this.getResourceTypeName()"}, type = AzureOperation.Type.SERVICE)
    protected R loadResourceFromAzure(@Nonnull String name, @Nullable String resourceGroup) {
        log.debug("[{}]:loadResourceFromAzure({}, {})", this.getName(), name, resourceGroup);
        final Object client = this.getClient();
        resourceGroup = StringUtils.firstNonBlank(resourceGroup, this.getParent().getResourceGroupName());
        resourceGroup = StringUtils.equals(resourceGroup, RESOURCE_GROUP_PLACEHOLDER) ? null : resourceGroup;
        if (client instanceof SupportsGettingById && StringUtils.isNotEmpty(resourceGroup)) {
            log.debug("[{}]:loadResourceFromAzure->client.getById({}, {})", this.name, resourceGroup, name);
            return this.<SupportsGettingById<R>>cast(client).getById(toResourceId(name, resourceGroup));
        } else if (client instanceof SupportsGettingByResourceGroup && StringUtils.isNotEmpty(resourceGroup)) {
            log.debug("[{}]:loadResourceFromAzure->client.getByResourceGroup({}, {})", this.name, resourceGroup, name);
            return this.<SupportsGettingByResourceGroup<R>>cast(client).getByResourceGroup(resourceGroup, name);
        } else if (client instanceof SupportsGettingByName) {
            log.debug("[{}]:loadResourceFromAzure->client.getByName({})", this.name, name);
            return this.<SupportsGettingByName<R>>cast(client).getByName(name);
        } else { // fallback to filter the named resource from all resources in current module.
            log.debug("[{}]:loadResourceFromAzure->this.list().filter({}).getRemote()", this.name, name);
            return this.list().stream().filter(r -> StringUtils.equals(name, r.getName())).findAny().map(AbstractAzResource::getRemote).orElse(null);
        }
    }

    @AzureOperation(
        name = "resource.delete_resource.resource|type",
        params = {"nameFromResourceId(resourceId)", "this.getResourceTypeName()"},
        type = AzureOperation.Type.SERVICE
    )
    protected void deleteResourceFromAzure(@Nonnull String resourceId) {
        log.debug("[{}]:deleteResourceFromAzure({})", this.getName(), resourceId);
        final Object client = this.getClient();
        if (client instanceof SupportsDeletingById) {
            log.debug("[{}]:deleteResourceFromAzure->client.deleteById({})", this.name, resourceId);
            ((SupportsDeletingById) client).deleteById(resourceId);
        }
    }

    @Nonnull
    protected AzResource.Draft<T, R> newDraftForCreate(@Nonnull String name, @Nullable String resourceGroup) {
        throw new AzureToolkitRuntimeException("not supported");
    }

    @Nonnull
    protected AzResource.Draft<T, R> newDraftForUpdate(@Nonnull T t) {
        throw new AzureToolkitRuntimeException("not supported");
    }

    @Nonnull
    protected abstract T newResource(@Nonnull R r);

    @Nonnull
    protected abstract T newResource(@Nonnull String name, @Nullable String resourceGroupName);

    /**
     * get track2 client, which is used to implement {@link #loadResourcesFromAzure}, {@link #loadResourceFromAzure} and {@link #deleteResourceFromAzure}
     */
    @Nullable
    protected Object getClient() {
        throw new AzureToolkitRuntimeException("not implemented");
    }

    @Nonnull
    private <D> D cast(@Nonnull Object origin) {
        //noinspection unchecked
        return (D) origin;
    }
}

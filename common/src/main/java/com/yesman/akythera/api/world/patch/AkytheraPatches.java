package com.yesman.akythera.api.world.patch;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.ClientOnly;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.api.world.patch.item.ItemPatch;
import com.yesman.akythera.core.blueprint.ItemPatchBlueprint;
import com.yesman.akythera.mixinaccessor.common.EntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// A util class that helps extract **patches**, the epic fight dedicated modules for in-game
/// objects like entities and items
@NullMarked
public final class AkytheraPatches {
    private AkytheraPatches() {}

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(AkytheraPatches::clearCachedItemPatches);
    }

    /// Cached [ItemPatch]s of the logical server after [StaticRegistryAccess#getRegistryAccess] is loaded
    ///
    /// Since item patches can't be built on data pack load due to its parent-child hierarchy
    /// the patch is initially assembled in first reference. Empty for an item without a patch.
    private static final Map<Item, Optional<ItemPatch>> SERVER_ITEM_PATCH_CACHE = new ConcurrentHashMap<>();

    /// The logical client's twin of [#SERVER_ITEM_PATCH_CACHE]. An integrated server and its client
    /// each build their own patches, so switching a component of one never reaches the other.
    private static final Map<Item, Optional<ItemPatch>> CLIENT_ITEM_PATCH_CACHE = new ConcurrentHashMap<>();

    /// Unload registry access when quitting a world
    private static void clearCachedItemPatches(RegistryAccessEvent.Unload event) {
        (event.getSide() == LogicalSide.SERVER ? SERVER_ITEM_PATCH_CACHE : CLIENT_ITEM_PATCH_CACHE).clear();
    }

    /// The patch of `itemStack`'s item on the calling thread's logical side, see [StaticRegistryAccess].
    ///
    /// One patch serves every stack of an item on that side, so a component switched on it (see
    /// [com.yesman.akythera.api.component.PatchComponent#setEnabled]) is switched for every holder of
    /// the item, not only for one entity or stack.
    public static @Nullable ItemPatch getItemPatch(ItemStack itemStack) {
        Item item = itemStack.getItem();
        Map<Item, Optional<ItemPatch>> cache = StaticRegistryAccess.isServerRegistryAccessInUse()
            ? SERVER_ITEM_PATCH_CACHE
            : CLIENT_ITEM_PATCH_CACHE;
        Optional<ItemPatch> cached = cache.get(item);

        if (cached == null) {
            ResourceKey<ItemPatchBlueprint> itemPatchResourceKey =
                ResourceKey.create(
                    AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
                    Identifier.parse(item.builtInRegistryHolder().getRegisteredName())
                );

            // Built outside the map, since building a patch may look up its parents' patches.
            Optional<ItemPatch> built = StaticRegistryAccess.getRegistryAccess().get(itemPatchResourceKey)
                .map(blueprint -> blueprint.value().instantiate(item));
            Optional<ItemPatch> raced = cache.putIfAbsent(item, built);

            cached = raced != null ? raced : built;
        }

        return cached.orElse(null);
    }

    /// This method should remain as the secondary option, especially when you can't fix local variables inside lambda expression.
    ///
    /// @param entity An entity object to extract an entity patch
    @SuppressWarnings("unchecked")
    public static <E extends Entity, T extends EntityPatch<E>> @Nullable T getEntityPatch(@Nullable Entity entity) {
        if (entity != null) {
            EntityPatch<?> entityPatchUncasted = ((EntityAccessor)entity).akythera$getEntityPatch();

            if (entityPatchUncasted != null) {
                try {
                    return (T)entityPatchUncasted;
                } catch(ClassCastException e) {
                    // If casting failed, the patch is not a compatible type
                    return null;
                }
            }
        }

        return null;
    }

    /// Returns an [Optional] instance that holds the execution result of [#getEntityPatch]
    public static <E extends Entity, T extends EntityPatch<E>> Optional<T> getEntityPatchOptional(@Nullable Entity entity) {
        return Optional.ofNullable(AkytheraPatches.<E, T>getEntityPatch(entity));
    }

    @ClientOnly
    public static class Clients {
        /// Returns [EntityPatch] from cached player in [Minecraft]
        /// Warning: developers must check physical & logical side before calling this method
        public static @Nullable EntityPatch<LocalPlayer> getCachedLocalPlayerPatch() {
            if (Minecraft.getInstance().player == null) {
                return null;
            }

            return getEntityPatch(Minecraft.getInstance().player);
        }

        /// Returns [EntityPatch] as an optional object from cached player in [Minecraft]
        /// Warning: developers must check physical & logical side before calling this method
        public static Optional<EntityPatch<LocalPlayer>> getCachedLocalPlayerPatchOptional() {
            return Optional.ofNullable(getCachedLocalPlayerPatch());
        }
    }
}

package com.yesman.akythera.core.util;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.registry.RegistryAccessEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// [RegistryAccess] manager to ease accessing the currently loaded
/// dynamic registry (data pack) by intercepting level login/disconnect
/// times.
///
/// Holds one registry access per logical side, because a physical client running an integrated
/// server (single player, LAN) has both loaded at once. The client's is handed out on the thread
/// that loaded it, the render thread. Every other thread gets the server's when a server is running,
/// so the server thread, its network threads and its world generation workers never read the
/// client's copy.
public final class StaticRegistryAccess {
    /// Registry access of the logical server, if one runs in this process
    private static volatile @Nullable RegistryAccess serverRegistryAccess;

    /// Registry access of the logical client, if this process joined a world
    private static volatile @Nullable RegistryAccess clientRegistryAccess;

    /// The thread [#clientRegistryAccess] was loaded on
    private static volatile @Nullable Thread clientThread;

    @ApiStatus.Internal
    public static void registerEvents() {
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_LOAD.registerEvent(StaticRegistryAccess::onRegistryAccessLoaded);
        BuiltInEventHooks.Registry.REGISTRY_ACCESS_UNLOAD.registerEvent(StaticRegistryAccess::onRegistryAccessUnloaded);
    }

    /// Load registry access when joining a world
    private static void onRegistryAccessLoaded(RegistryAccessEvent.Load event) {
        if (event.getSide() == LogicalSide.SERVER) {
            serverRegistryAccess = event.getRegistryAccess();
        } else {
            clientThread = Thread.currentThread();
            clientRegistryAccess = event.getRegistryAccess();
        }
    }

    /// Unload registry access when quitting a world
    private static void onRegistryAccessUnloaded(RegistryAccessEvent.Unload event) {
        if (event.getSide() == LogicalSide.SERVER) {
            serverRegistryAccess = null;
        } else {
            clientRegistryAccess = null;
            clientThread = null;
        }
    }

    /// Whether the calling thread reads the logical server's registry access
    public static boolean isServerRegistryAccessInUse() {
        return selectSide() == LogicalSide.SERVER;
    }

    private static LogicalSide selectSide() {
        if (clientRegistryAccess != null && Thread.currentThread() == clientThread) return LogicalSide.CLIENT;

        return serverRegistryAccess != null ? LogicalSide.SERVER : LogicalSide.CLIENT;
    }

    /// Returns a currently loaded registry access. Must have any loaded registry access. In other words there must be
    /// a world that player joined.
    public static @NonNull RegistryAccess getRegistryAccess() throws IllegalStateException {
        RegistryAccess registryAccess = getRegistryAccessUnsafe();

        if (registryAccess == null) {
            throw new IllegalStateException("Tried to access registries before loaded. You must join a world.");
        }

        return registryAccess;
    }

    /// Returns a currently loaded registry access. Doesn't check the null so use it at your own risk.
    public static @Nullable RegistryAccess getRegistryAccessUnsafe() {
        return selectSide() == LogicalSide.SERVER ? serverRegistryAccess : clientRegistryAccess;
    }

    /// Debug Only: dereference the [Identifier] from currently loaded registry access.
    /// Avoid calling [#toString] of this item in the product side.
    public static <T> String getRegisteredName(ResourceKey<@NonNull Registry<@NonNull T>> registryKey, T object) {
        if (getRegistryAccessUnsafe() == null) {
            return "Dangling " + object.getClass().getSimpleName();
        }

        // Doesn't handle exception from 'lookupOrThrow' here. If it throws an error the whole mod system is broken.
        Identifier identifier = getRegistryAccess().lookupOrThrow(registryKey).getKey(object);

        if (identifier == null) {
            return "Dangling " + object.getClass().getSimpleName();
        }

        return identifier.toString();
    }


    private StaticRegistryAccess() {}
}

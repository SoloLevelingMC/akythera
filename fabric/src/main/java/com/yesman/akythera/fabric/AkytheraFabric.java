package com.yesman.akythera.fabric;

import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.EntityTrackingEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.networking.NetworkingHelper;
import com.yesman.akythera.fabric.initializer.ComponentFactoryInitializer;
import com.yesman.akythera.fabric.initializer.EnumerableDataBlockInitializer;
import com.yesman.akythera.fabric.networking.FabricNetworkingHelper;
import com.yesman.akythera.fabric.networking.ServerBoundNetworkReceiverRegistrar;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

import java.util.function.Consumer;

public final class AkytheraFabric implements ModInitializer {
    public AkytheraFabric() {
        Akythera.onModConstructed();

        // Defines an entry point for addons to register their custom entry to ComponentsFactory
        invokeEntrypoints(
            "component_factory_initializer",
            ComponentFactoryInitializer.class,
            "register entity patches and components to ComponentsFactory",
            initializer -> initializer.onFactoryInitialized(ComponentsFactory.getImpl())
        );

        Akythera.registerBuiltInDataBlockClasses();

        // Defines an entry point for addons to register enumerable data block enum classes
        invokeEntrypoints(
            "enumerable_data_block_initializer",
            EnumerableDataBlockInitializer.class,
            "register enumerable data block classes",
            EnumerableDataBlockInitializer::registerEnums
        );
    }

    /// Calls every addon's `key` entry point. A failing one is logged with the mod and the class that threw, then
    /// crashes the game in a development environment: a half-registered addon otherwise fails later, far from the
    /// cause (e.g. enum ids that differ between the server and its clients).
    private static <T> void invokeEntrypoints(
        final String key,
        final Class<T> type,
        final String action,
        final Consumer<T> invoker
    ) {
        for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers(key, type)) {
            String modId = container.getProvider().getMetadata().getId();

            try {
                invoker.accept(container.getEntrypoint());
            } catch (RuntimeException | LinkageError e) {
                Akythera.LOGGER.error(
                    "Mod {} failed to {} in its '{}' entry point {}",
                    modId,
                    action,
                    key,
                    container.getDefinition(),
                    e
                );

                if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
                    throw new IllegalStateException("Mod " + modId + " failed to " + action + " in its '" + key + "' entry point", e);
                }
            }
        }
    }

    @Override
    public void onInitialize() {
        Akythera.onModInitialized();
        NetworkingHelper.initialize(new FabricNetworkingHelper());

        // Initialize networking payload types & receivers
        ServerBoundNetworkReceiverRegistrar.registerTypes();
        ServerBoundNetworkReceiverRegistrar.registerReceivers();

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> Akythera.onRegisterCommands(dispatcher));

        registerTrackingEvents();
    }

    /// Posts [BuiltInEventHooks.Entity#TRACKING_START] and `TRACKING_END`, which the engine declares
    /// and listens to but no loader fired.
    private static void registerTrackingEvents() {
        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) ->
            AkytheraPatches.getEntityPatchOptional(trackedEntity).ifPresent(entityPatch ->
                BuiltInEventHooks.Entity.TRACKING_START.postWithListener(
                    new EntityTrackingEvent.Start(entityPatch, player),
                    entityPatch.getEntityEventListener()
                )
            )
        );

        EntityTrackingEvents.STOP_TRACKING.register((trackedEntity, player) ->
            AkytheraPatches.getEntityPatchOptional(trackedEntity).ifPresent(entityPatch ->
                BuiltInEventHooks.Entity.TRACKING_END.postWithListener(
                    new EntityTrackingEvent.Stop(entityPatch, player),
                    entityPatch.getEntityEventListener()
                )
            )
        );
    }
}

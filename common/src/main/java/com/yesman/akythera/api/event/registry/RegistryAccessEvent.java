package com.yesman.akythera.api.event.registry;

import com.yesman.akythera.core.event.Event;
import com.yesman.akythera.core.util.LogicalSide;
import net.minecraft.core.RegistryAccess;

/// Called on [RegistryAccess] created or destroyed
///
/// A physical client running an integrated server (single player, LAN) holds two registry accesses
/// at once, one per logical side. [#getSide] tells which one an event is about.
public abstract class RegistryAccessEvent extends Event {
    private final LogicalSide side;

    protected RegistryAccessEvent(final LogicalSide side) {
        this.side = side;
    }

    /// The logical side whose registry access this is, never [LogicalSide#BOTH]
    public final LogicalSide getSide() {
        return side;
    }

    /// In a physical client, this even called when the player joins a world,
    /// receiving a frozen data registry packet from server.
    ///
    /// In a physical server, this event called only once the game launches
    public static final class Load extends RegistryAccessEvent {
        private final RegistryAccess registryAccess;

        /// A logical client's registry access
        public Load(RegistryAccess registryAccess) {
            this(registryAccess, LogicalSide.CLIENT);
        }

        public Load(RegistryAccess registryAccess, LogicalSide side) {
            super(side);
            this.registryAccess = registryAccess;
        }

        /// Returns a created registry access
        public final RegistryAccess getRegistryAccess() {
            return registryAccess;
        }
    }

    /// In physical client, this even called when the player quit a world
    ///
    /// In physical server, this event called only once the game terminated
    public static final class Unload extends RegistryAccessEvent {
        /// A logical client's registry access
        public Unload() {
            this(LogicalSide.CLIENT);
        }

        public Unload(LogicalSide side) {
            super(side);
        }
    }
}

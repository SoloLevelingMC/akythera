package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.core.reflection.ScriptType;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/// The variables one [Ability] reads and writes, one per [AbilityInstance], built by
/// [Ability#createState].
///
/// A null codec in a direction means the state is not a sync target that way. Fields a codec
/// leaves out keep their current value on the receiving side.
///
/// Nothing travels from client to server unless the state opts in by overriding
/// [#serverBoundCodec]: whatever that codec reads, a client controls.
///
/// Every sync method defaults to "no sync": a state that lives on the server only declares its
/// fields and nothing else. A state that syncs overrides [#streamCodec], [#checkDirtyToSend],
/// [#copyState] and [#decodeInto] together.
@ScriptType
public interface AbilityState {
    /// The state an ability that declares no variables receives: nothing to sync, never dirty.
    AbilityState EMPTY = new AbilityState() {
    };

    /// The codec both directions use unless one is overridden. Null by default: the state never
    /// leaves the side that owns it.
    default @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> streamCodec() {
        return null;
    }

    /// The fields a client may write into the server's state when it triggers the ability. Null by
    /// default, so a state never accepts client input it did not explicitly ask for.
    default @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> serverBoundCodec() {
        return null;
    }

    default @Nullable StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> clientBoundCodec() {
        return streamCodec();
    }

    /// Whether any field the client bound codec carries differs from `previous`, the last state
    /// that was sent. False by default, so a state with no codec is never sent.
    default boolean checkDirtyToSend(final AbilityState previous) {
        return false;
    }

    /// Copies `from`'s values into this state in place. Does nothing by default: only the sync
    /// baseline and an atomic decode copy a state.
    default void copyState(final AbilityState from) {

    }

    /// Reads what the `flow` codec wrote into this state in place. Reads nothing by default.
    ///
    /// Every field that codec leaves out keeps its current value.
    ///
    /// @param level resolves entity ids, which a codec alone cannot
    default void decodeInto(final PacketFlow flow, final RegistryFriendlyByteBuf buffer, final Level level) {

    }

    /// Encodes `state` through its `flow` codec on the calling thread. Empty when that direction has
    /// no codec.
    ///
    /// A connection encodes its payloads later on its own event loop, where a live state would be
    /// read while its owner keeps writing it.
    @SuppressWarnings("unchecked")
    static byte[] encode(
        final AbilityState state,
        final PacketFlow flow,
        final RegistryAccess registryAccess
    ) {
        StreamCodec<? super RegistryFriendlyByteBuf, ? extends AbilityState> codec =
            flow == PacketFlow.CLIENTBOUND ? state.clientBoundCodec() : state.serverBoundCodec();

        if (codec == null) return new byte[0];

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);

        try {
            // A state declares its codecs for its own class, and this codec came from this state.
            ((StreamCodec<? super RegistryFriendlyByteBuf, AbilityState>) codec).encode(buffer, state);

            return ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    /// Reads `bytes` into `state` in place.
    ///
    /// Use [#decodeAtomically] for bytes from an untrusted sender.
    ///
    /// @return whether the bytes were exactly one encoding. Fields read before a fault stay written
    static boolean decode(
        final AbilityState state,
        final PacketFlow flow,
        final byte[] bytes,
        final Level level
    ) {
        RegistryFriendlyByteBuf buffer =
            new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), level.registryAccess());

        try {
            state.decodeInto(flow, buffer, level);

            return buffer.readableBytes() == 0;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            buffer.release();
        }
    }

    /// Reads `bytes` into `scratch`, a copy of `state`, and copies the result back into `state` only
    /// when the bytes were exactly one encoding. A fault leaves `state` untouched.
    ///
    /// @param scratch a state of the same ability as `state`, built by [Ability#createState]. Its
    ///                values are overwritten
    /// @return whether `state` was written
    static boolean decodeAtomically(
        final AbilityState state,
        final AbilityState scratch,
        final PacketFlow flow,
        final byte[] bytes,
        final Level level
    ) {
        if (scratch == state) return decode(state, flow, bytes, level);

        scratch.copyState(state);

        if (!decode(scratch, flow, bytes, level)) return false;

        state.copyState(scratch);

        return true;
    }
}

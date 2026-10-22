package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.api.world.ability.AbilityEffectInstance;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.core.Akythera;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// Where one live [AbilityEffectInstance] stands on the server, named by its effect id and the
/// modifier index the server numbered it with. `ended` drops the client's copy; anything else
/// creates or overwrites it.
///
/// The modifier list, the resource deltas and the applied flag stay on the server. The effect's
/// application type, duration and stack bound do travel: a client finds no effect a Java built
/// ability declared, and counts its copy with these.
@NullMarked
public record ClientBoundAbilityEffectSync(
    int entityId,
    Identifier effectId,
    int modifierIndex,
    AbilityEffectInstance.Role role,
    Optional<AbilitySlot> source,
    int remainingTicks,
    int stacks,
    boolean ended,
    AbilityEffect.ApplicationType applicationType,
    int duration,
    int maxStack
) implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, AbilityEffectInstance.Role> ROLE_STREAM_CODEC =
        ByteBufCodecs.idMapper(
            ordinal -> AbilityEffectInstance.Role.values()[ordinal],
            AbilityEffectInstance.Role::ordinal
        );

    private static final StreamCodec<ByteBuf, AbilityEffect.ApplicationType> APPLICATION_TYPE_STREAM_CODEC =
        ByteBufCodecs.idMapper(
            ordinal -> AbilityEffect.ApplicationType.values()[ordinal],
            AbilityEffect.ApplicationType::ordinal
        );

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<AbilitySlot>> SOURCE_STREAM_CODEC =
        ByteBufCodecs.optional(AbilitySlot.ENUM_MANAGER.getStreamCodec());

    public static final CustomPacketPayload.Type<ClientBoundAbilityEffectSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_effect_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityEffectSync> STREAM_CODEC =
        StreamCodec.of(
            (buffer, payload) -> {
                ByteBufCodecs.INT.encode(buffer, payload.entityId());
                Identifier.STREAM_CODEC.encode(buffer, payload.effectId());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.modifierIndex());
                ROLE_STREAM_CODEC.encode(buffer, payload.role());
                SOURCE_STREAM_CODEC.encode(buffer, payload.source());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.remainingTicks());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.stacks());
                ByteBufCodecs.BOOL.encode(buffer, payload.ended());
                APPLICATION_TYPE_STREAM_CODEC.encode(buffer, payload.applicationType());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.duration());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.maxStack());
            },
            buffer -> new ClientBoundAbilityEffectSync(
                ByteBufCodecs.INT.decode(buffer),
                Identifier.STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ROLE_STREAM_CODEC.decode(buffer),
                SOURCE_STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.BOOL.decode(buffer),
                APPLICATION_TYPE_STREAM_CODEC.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer)
            )
        );

    public static ClientBoundAbilityEffectSync of(
        final int entityId,
        final AbilityEffectInstance instance,
        final boolean ended
    ) {
        AbilityInstance source = instance.getSource();

        return new ClientBoundAbilityEffectSync(
            entityId,
            instance.getEffect().getId(),
            instance.getModifierIndex(),
            instance.getRole(),
            Optional.ofNullable(source == null ? null : source.getSlot()),
            instance.getRemainingTicks(),
            instance.getStacks(),
            ended,
            instance.getEffect().getApplicationType(),
            instance.getEffect().getDuration(),
            instance.getEffect().getMaxStack()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

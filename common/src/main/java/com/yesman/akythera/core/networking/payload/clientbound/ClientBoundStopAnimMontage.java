package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// A montage the server stopped on the entity `entityId`. An empty `montageId` stops whatever
/// montage the entity plays.
@NullMarked
public record ClientBoundStopAnimMontage(Optional<Identifier> montageId, int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundStopAnimMontage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "stop_anim_montage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundStopAnimMontage> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.optional(Identifier.STREAM_CODEC),
            ClientBoundStopAnimMontage::montageId,
            ByteBufCodecs.INT,
            ClientBoundStopAnimMontage::entityId,
            ClientBoundStopAnimMontage::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

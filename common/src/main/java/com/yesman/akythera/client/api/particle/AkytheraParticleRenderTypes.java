package com.yesman.akythera.client.api.particle;

import net.minecraft.client.particle.ParticleRenderType;

import java.util.List;

/// The particle groups Akythera adds to the particle engine, drawn after vanilla's in [#RENDER_ORDER].
public final class AkytheraParticleRenderTypes {
    private AkytheraParticleRenderTypes() {}

    /// [AkytheraMeshParticle]s, drawn by [AkytheraMeshParticleGroup].
    public static final ParticleRenderType MESH = new ParticleRenderType("AKYTHERA_MESH", "AM");

    // SoloLevelingMC fork: the TRAIL group went away with TrailRenderer, which needed compute shaders.
    public static final List<ParticleRenderType> RENDER_ORDER = List.of(MESH);
}

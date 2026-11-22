package com.mrgregles.bsp_core.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** A gold spark drifting up from the Score Leader's totem, swaying a little, fading in and out, full bright. */
public class LeaderSparkParticle extends TextureSheetParticle {
    private final float sway;

    protected LeaderSparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.995f;
        this.gravity = 0;
        this.hasPhysics = false;
        this.lifetime = 60 + random.nextInt(25);
        this.quadSize = 0.045f + random.nextFloat() * 0.03f;
        boolean pale = random.nextFloat() < 0.3f;
        setColor(1f, pale ? 0.95f : 0.8f, pale ? 0.7f : 0.3f);
        this.alpha = 0;
        this.sway = random.nextFloat() * 6.28f;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.xd += Mth.sin(age * 0.15f + sway) * 0.0006;
        this.alpha = Mth.sin((float) Math.PI * Math.min(1f, age / (float) lifetime));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0; // full bright: the spark is its own light
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new LeaderSparkParticle(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}

package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes vertices through with their alpha scaled down, so a block model can be drawn see-through (on a translucent layer). */
public record FaintConsumer(VertexConsumer to, float alpha) implements VertexConsumer {
    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        to.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        to.color(r, g, b, Math.round(a * alpha));
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        to.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        to.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        to.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        to.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        to.endVertex();
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        to.defaultColor(r, g, b, Math.round(a * alpha));
    }

    @Override
    public void unsetDefaultColor() {
        to.unsetDefaultColor();
    }
}

/*
 * This file is part of LudicrousHooks, licensed under the MIT License (MIT).
 *
 * Copyright (c) TechShroom <https://techshroom.com>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.techshroom.ludicroushooks.entity.render;

import static org.lwjgl.opengl.GL11.GL_QUADS;

import org.lwjgl.util.vector.Quaternion;

import com.techshroom.ludicroushooks.VecUtil;
import com.techshroom.ludicroushooks.entity.EntityHook;
import com.techshroom.ludicroushooks.proxy.ClientProxy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VertexBuffer;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(Side.CLIENT)
public class HookRenderer extends Render<EntityHook> {

    // in blocks
    private static final double CHAIN_WIDTH = 1 / 16d;
    private static final ResourceLocation CHAIN =
            new ResourceLocation("ludicroushooks", "textures/entity/chain.png");

    public HookRenderer(RenderManager renderManagerIn) {
        super(renderManagerIn);
    }

    @Override
    public void doRender(EntityHook entity, double x, double y, double z,
            float entityYaw, float partialTicks) {
        // Position calculations
        IBakedModel model = ClientProxy.getModel(ClientProxy.GRAPPLE_HOOK);
        // lerp is in [0, 1] for time entity has traveled
        long targetNanos = entity.getTargetNanos();
        long startTime = targetNanos - entity.getTravelTime();
        double lerp = targetNanos > 0 ? ((double) System.nanoTime() - startTime)
                / (targetNanos - startTime) : 0;
        // cap lerp to 1
        lerp = Math.min(lerp, 1);

        // get firer, return immediately if not present
        Entity firer = entity.getOwnerEntity();
        if (firer == null) {
            return;
        }
        Entity renderEntity = Minecraft.getMinecraft().getRenderViewEntity();

        // Offset to render position
        Vec3d renderELerpd = getPositionLerp(renderEntity, partialTicks);

        Vec3d look = entity.getLook(1.0F);
        // offset into hooked block
        Vec3d hookInBlockOffset = VecUtil.setLength(look, 0.1);

        // Place where the entity is attached in the world
        Vec3d worldAttachPos =
                entity.getAttachPosition().add(hookInBlockOffset);

        // Vector representing the difference from the render pos
        // used for proper rendering coords
        Vec3d posDiff = worldAttachPos.subtract(renderELerpd);
        // Re-scale diff to get current position of rendering
        Vec3d rescaledPos = posDiff.scale(lerp);
        double newX = rescaledPos.xCoord;
        double newY = rescaledPos.yCoord;
        double newZ = rescaledPos.zCoord;

        // Draw the chain at the correct location
        // Taking into account the firing position
        Vec3d gunLocation = EntityHook.getGunLocation(firer, partialTicks);
        drawChain(firer, gunLocation,
                entity.getPullPosition().subtract(gunLocation).scale(lerp),
                renderELerpd, partialTicks);

        // Rotation calc
        float pitch = entity.rotationPitch;
        float yaw = 360 - entity.rotationYaw;
        float pitchRad = (float) Math.toRadians(pitch / 2);
        Quaternion pitchQ = new Quaternion(MathHelper.sin(pitchRad), 0, 0,
                MathHelper.cos(pitchRad));
        float yawRad = (float) Math.toRadians(yaw / 2);
        Quaternion yawQ = new Quaternion(0, MathHelper.sin(yawRad), 0,
                MathHelper.cos(yawRad));
        Quaternion rollQ = new Quaternion();
        Quaternion totalQ = new Quaternion();
        Quaternion.mul(yawQ, pitchQ, totalQ);
        Quaternion.mul(totalQ, rollQ, totalQ);

        GlStateManager.translate((float) newX, (float) newY, (float) newZ);
        GlStateManager.rotate(totalQ);

        if (this.renderOutlines) {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableOutlineMode(this.getTeamColor(entity));
        }

        this.bindEntityTexture(entity);
        Minecraft.getMinecraft().getBlockRendererDispatcher()
                .getBlockModelRenderer()
                .renderModelBrightnessColor(model, 1.0F, 1.0F, 1.0F, 1.0F);

        if (this.renderOutlines) {
            GlStateManager.disableOutlineMode();
            GlStateManager.disableColorMaterial();
        }

        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.doRender(entity, newX, newY, newZ, entityYaw, partialTicks);
    }

    /**
     * Draws the chain from the firer's hand to the hook.
     * 
     * @param firer
     *            - The firing entity
     * @param fireLoc
     *            - The position, in world coords, of the firer
     * @param posDiff
     *            - The position, in world coords, of the hook
     * @param renderCoordOffset
     *            - The offset to subtract for rendering
     * @param partialTicks
     *            - The render partial ticks
     */
    private void drawChain(Entity firer, Vec3d fireLoc, Vec3d posDiff,
            Vec3d renderCoordOffset, float partialTicks) {
        Vec3d renderFireLoc = fireLoc.subtract(renderCoordOffset);
        // Vector from gunLocation to current position (rescaledPos)
        // Special pitch calculation
        double distForY = MathHelper.sqrt_double(posDiff.xCoord * posDiff.xCoord
                + posDiff.zCoord * posDiff.zCoord);
        // yaw -- y, rotate first
        // pitch -- z, rotate last
        float yaw = (float) (360 - Math
                .toDegrees((MathHelper.atan2(posDiff.zCoord, posDiff.xCoord))));
        float pitch = (float) Math
                .toDegrees(MathHelper.atan2(posDiff.yCoord, distForY));
        // System.err.println(yaw + "," + pitch);
        // yaw = 385;
        // pitch = -6.8f;

        this.bindTexture(CHAIN);
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        // Translate to firing position (in render coords)
        GlStateManager.translate(renderFireLoc.xCoord, renderFireLoc.yCoord,
                renderFireLoc.zCoord);
        // Rotate here for the chain drawing
        GlStateManager.rotate(yaw, 0, 1, 0);
        GlStateManager.rotate(pitch, 0, 0, 1);

        Tessellator tess = Tessellator.getInstance();
        VertexBuffer vb = tess.getBuffer();
        renderQuad(tess, vb, posDiff.lengthVector());

        // Unrotate from the chain drawing
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    private void renderQuad(Tessellator tess, VertexBuffer vb, double length) {
        double yq = 0;
        double x1 = 0;
        double z1 = -CHAIN_WIDTH / 2;
        double x2 = length;
        double z2 = CHAIN_WIDTH / 2;
        vb.begin(GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        vb.pos(x1, yq, z2).tex(0, 1).endVertex();
        vb.pos(x2, yq, z2).tex(x2, 1).endVertex();
        vb.pos(x2, yq, z1).tex(x2, 0).endVertex();
        vb.pos(x1, yq, z1).tex(0, 0).endVertex();
        tess.draw();
    }

    private Vec3d getPositionLerp(Entity entity, float partialTicks) {
        return new Vec3d(lerp(entity.lastTickPosX, entity.posX, partialTicks),
                lerp(entity.lastTickPosY, entity.posY, partialTicks),
                lerp(entity.lastTickPosZ, entity.posZ, partialTicks));
    }

    private static double lerp(double from, double to, float lerpFactor) {
        return from + (to - from) * lerpFactor;
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityHook entity) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }

}

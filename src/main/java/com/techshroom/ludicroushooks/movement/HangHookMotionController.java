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
package com.techshroom.ludicroushooks.movement;

import com.techshroom.ludicroushooks.entity.EntityHangHook;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;

public class HangHookMotionController
        extends MotionControllerBase<EntityHangHook> {

    private Vec3d motion;

    public HangHookMotionController(EntityHangHook associatedHook) {
        super(associatedHook);
        Entity e = associatedHook.getOwnerEntity();
        this.motion = new Vec3d(e.motionX, e.motionY, e.motionZ);
    }

    @Override
    public void updatePlayerMotion(EntityPlayer e) {
        applyCollisions();
        Vec3d eVec = getAssociatedHook().getPullPosition();
        if (eVec == null) {
            return;
        }
        Vec3d pVec = e.getPositionVector().addVector(0, e.getEyeHeight(), 0);

        e.motionX = this.motion.xCoord;
        e.motionY = this.motion.yCoord;
        e.motionZ = this.motion.zCoord;

        this.motion = dampenMotion(this.motion, Vec3d.ZERO);
    }

    private static Vec3d dampenMotion(Vec3d motion, Vec3d forward) {
        Vec3d projected = project(motion, forward);
        double dampening = 0.05;
        double invDamp = 1 - dampening;
        return new Vec3d(projected.xCoord * dampening + motion.xCoord * invDamp,
                projected.yCoord * dampening + motion.yCoord * invDamp,
                projected.zCoord * dampening + motion.zCoord * invDamp);
    }

    private static Vec3d project(Vec3d vec, Vec3d project) {
        Vec3d v3 = vec.normalize();
        double dot = vec.dotProduct(v3);
        double len = v3.lengthVector();
        return v3.scale(dot / len);
    }

    private void applyCollisions() {
        EntityPlayer entity = getAssociatedHook().getOwnerEntity();
        if (entity.isCollidedHorizontally) {
            if (entity.motionX == 0) {
                this.motion =
                        new Vec3d(0, this.motion.yCoord, this.motion.zCoord);
            }
            if (entity.motionZ == 0) {
                this.motion =
                        new Vec3d(this.motion.zCoord, this.motion.yCoord, 0);
            }
        }
        if (entity.isCollidedVertically) {
            if (entity.motionY == 0) {
                this.motion =
                        new Vec3d(this.motion.xCoord, 0, this.motion.zCoord);
            }
        }
    }

}

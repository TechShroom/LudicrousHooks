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
package com.techshroom.ludicroushooks;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class VecUtil {

    public static Vec3d lookingVector(double x1, double y1, double z1,
            double x2, double y2, double z2) {
        double d0 = x1 - x2;
        double d1 = y1 - y2;
        double d2 = z1 - z2;
        double d3 = MathHelper.sqrt_double(d0 * d0 + d2 * d2);
        double pitch = (-(MathHelper.atan2(d1, d3) * (180D / Math.PI)));
        double yaw = (MathHelper.atan2(d2, d0) * (180D / Math.PI)) - 90.0;
        return fromPitchYaw(pitch, yaw);
    }

    public static Vec3d fromPitchYaw(double p, double y) {
        float yawCos = MathHelper.cos((float) (-y * 0.017453292 - Math.PI));
        float yawSin = MathHelper.sin((float) (-y * 0.017453292 - Math.PI));
        float pitCos = -MathHelper.cos((float) (-p * 0.017453292));
        float pitSin = MathHelper.sin((float) (-p * 0.017453292));
        return new Vec3d(yawSin * pitCos, pitSin, yawCos * pitCos);
    }

    public static Vec3d setLength(Vec3d v, double d) {
        return v.scale(d / v.lengthVector());
    }
}

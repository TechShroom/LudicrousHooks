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
package com.techshroom.ludicroushooks.entity;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializer;
import net.minecraft.util.math.Vec3d;

public enum Vec3dDataSerializer implements DataSerializer<Vec3d> {
    INSTANCE;

    private static final int NORM = 0b00;
    private static final int NULL = 0b01;
    private static final int ZERO = 0b10;

    @Override
    public void write(PacketBuffer buf, Vec3d value) {
        if (value == null) {
            buf.writeByte(NULL);
        } else if (value == Vec3d.ZERO) {
            buf.writeByte(ZERO);
        } else {
            buf.writeByte(NORM);
            buf.writeDouble(value.xCoord);
            buf.writeDouble(value.yCoord);
            buf.writeDouble(value.zCoord);
        }
    }

    @Override
    public Vec3d read(PacketBuffer buf) throws IOException {
        byte state = buf.readByte();
        if (state == NULL) {
            return null;
        }
        if (state == ZERO) {
            return Vec3d.ZERO;
        }
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        return new Vec3d(x, y, z);
    }

    @Override
    public DataParameter<Vec3d> createKey(int id) {
        return new DataParameter<>(id, this);
    }

}

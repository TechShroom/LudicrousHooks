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
package com.techshroom.ludicroushooks.json;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

// TODO maybe change how this works, could output chunks of the data or similar
public class NBTAdapter extends TypeAdapter<NBTTagCompound> {

    public static final NBTAdapter INSTANCE = new NBTAdapter();
    private static final byte[] ZERO_BYTES = {};

    private NBTAdapter() {
    }

    @Override
    public void write(JsonWriter out, NBTTagCompound value) throws IOException {
        String b64;
        try (ByteArrayOutputStream cap = new ByteArrayOutputStream();
                Base64OutputStream stream =
                        new Base64OutputStream(cap, true, 0, ZERO_BYTES)) {
            CompressedStreamTools.writeCompressed(value, stream);
            b64 = cap.toString(StandardCharsets.UTF_8.name());
        }
        out.value(b64);
    }

    @Override
    public NBTTagCompound read(JsonReader in) throws IOException {
        String b64 = in.nextString();
        try (ByteArrayInputStream b64s =
                new ByteArrayInputStream(b64.getBytes(StandardCharsets.UTF_8));
                Base64InputStream stream = new Base64InputStream(b64s)) {
            return CompressedStreamTools.readCompressed(stream);
        }
    }

}

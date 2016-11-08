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

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class MultimapAdapter implements TypeAdapterFactory {

    private static final class Adapter<K, V>
            extends TypeAdapter<Multimap<K, V>> {

        private final TypeAdapter<Map<K, Collection<V>>> mapAdapter;

        @SuppressWarnings("unchecked")
        public Adapter(TypeAdapter<?> mapAdapter) {
            this.mapAdapter = (TypeAdapter<Map<K, Collection<V>>>) mapAdapter;
        }

        @Override
        public void write(JsonWriter out, Multimap<K, V> value)
                throws IOException {
            this.mapAdapter.write(out, value.asMap());
        }

        @Override
        public Multimap<K, V> read(JsonReader in) throws IOException {
            Map<K, Collection<V>> collection = this.mapAdapter.read(in);
            ImmutableMultimap.Builder<K, V> b = ImmutableMultimap.builder();
            collection.forEach(b::putAll);
            return b.build();
        }

    }

    @SuppressWarnings("serial")
    static <K, V> TypeToken<Map<K, V>> mapOf(TypeToken<K> keyType,
            TypeToken<V> valueType) {
        return new TypeToken<Map<K, V>>() {}
                .where(new TypeParameter<K>() {}, keyType)
                .where(new TypeParameter<V>() {}, valueType);
    }

    @SuppressWarnings("serial")
    static <V> TypeToken<Collection<V>> collectionOf(TypeToken<V> valueType) {
        return new TypeToken<Collection<V>>() {}
                .where(new TypeParameter<V>() {}, valueType);
    }

    private TypeToken<?> multimapToMap(TypeToken<?> multimap) {
        Type multimapType = multimap.getType();
        if (multimapType instanceof Class) {
            // rawtype
            return TypeToken.of(Map.class);
        } else if (multimapType instanceof ParameterizedType) {
            // expected type
            Type[] args =
                    ((ParameterizedType) multimapType).getActualTypeArguments();
            return mapOf(TypeToken.of(args[0]),
                    collectionOf(TypeToken.of(args[1])));
        }
        // unknown type
        throw new UnsupportedOperationException(
                "Cannot understand type of class "
                        + multimapType.getClass().getName());
    }

    public static final MultimapAdapter INSTANCE = new MultimapAdapter();

    private MultimapAdapter() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson,
            com.google.gson.reflect.TypeToken<T> type) {
        if (!Multimap.class.isAssignableFrom(type.getRawType())) {
            return null;
        }
        TypeToken<T> guavaType =
                (TypeToken<T>) multimapToMap(TypeToken.of(type.getType()));
        return (TypeAdapter<T>) new Adapter<Object, Object>(gson.getAdapter(
                com.google.gson.reflect.TypeToken.get(guavaType.getType())));
    }

}
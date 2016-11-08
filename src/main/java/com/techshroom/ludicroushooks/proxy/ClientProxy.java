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
package com.techshroom.ludicroushooks.proxy;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import com.techshroom.ludicroushooks.Constants;
import com.techshroom.ludicroushooks.LudicrousHooks;
import com.techshroom.ludicroushooks.entity.EntityHook;
import com.techshroom.ludicroushooks.entity.render.HookRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@EventBusSubscriber(value = Side.CLIENT)
public class ClientProxy implements Proxy {

    @Override
    public void onPreInit(FMLPreInitializationEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(EntityHook.class,
                HookRenderer::new);
    }

    @SubscribeEvent
    public static void onMRLRegistration(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(LudicrousHooks.MOTOR_GRAPPLE_GUN, 0,
                new ModelResourceLocation(
                        new ResourceLocation(Constants.MOD_ID, "grapplegun"),
                        "hook=true"));
        ModelLoader.setCustomModelResourceLocation(LudicrousHooks.MOTOR_GRAPPLE_GUN, 1,
                new ModelResourceLocation(
                        new ResourceLocation(Constants.MOD_ID, "grapplegun"),
                        "hook=false"));
    }

    public static final ResourceLocation GRAPPLE_HOOK =
            new ResourceLocation(Constants.MOD_ID, "block/grapple");

    private static ModelManager modelManager;
    private static final List<ResourceLocation> MODELS =
            ImmutableList.of(GRAPPLE_HOOK);
    private static final Map<ResourceLocation, IBakedModel> BAKED_MODELS =
            new HashMap<>();

    public static IBakedModel getModel(ResourceLocation location) {
        IBakedModel model = BAKED_MODELS.getOrDefault(location,
                modelManager.getMissingModel());
        checkNotNull(model, "null was explicitly put into BAKED_MODELS!");
        return model;
    }

    @SubscribeEvent
    public static void onMLBake(ModelBakeEvent event) {
        modelManager = event.getModelManager();
        for (ResourceLocation loc : MODELS) {
            try {
                IBakedModel baked = null;
                if (loc instanceof ModelResourceLocation) {
                    baked = modelManager.getModel((ModelResourceLocation) loc);
                }
                if (baked == null || baked == modelManager.getMissingModel()) {
                    IModel model = ModelLoaderRegistry.getModel(loc);
                    baked = model.bake(model.getDefaultState(),
                            DefaultVertexFormats.ITEM,
                            ModelLoader.defaultTextureGetter());
                }
                BAKED_MODELS.put(loc, baked);
            } catch (Exception e) {
                LudicrousHooks.LOGGER.warn("Failed to load model for " + loc, e);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderItemInHand(RenderSpecificHandEvent event) {
        // All we want to do is not draw cooldown on the item...
        ItemStack stack = event.getItemStack();
        if (stack != null && stack.getItem() == LudicrousHooks.MOTOR_GRAPPLE_GUN) {
            event.setCanceled(true);
            Minecraft.getMinecraft().getItemRenderer().renderItemInFirstPerson(
                    Minecraft.getMinecraft().thePlayer, event.getPartialTicks(),
                    event.getInterpolatedPitch(), event.getHand(),
                    event.getSwingProgress(), stack, 0.0F);
        }
    }

    @Override
    public boolean thisClientIsUUID(UUID owner) {
        return Minecraft.getMinecraft().thePlayer.getUniqueID().equals(owner);
    }

}

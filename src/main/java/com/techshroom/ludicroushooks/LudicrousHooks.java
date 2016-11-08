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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.techshroom.ludicroushooks.entity.EntityHangHook;
import com.techshroom.ludicroushooks.entity.EntityHook;
import com.techshroom.ludicroushooks.entity.EntityMotorizedHook;
import com.techshroom.ludicroushooks.entity.LongDataSerializer;
import com.techshroom.ludicroushooks.entity.UUIDDataSerializer;
import com.techshroom.ludicroushooks.entity.Vec3dDataSerializer;
import com.techshroom.ludicroushooks.item.ItemHangGrappleGun;
import com.techshroom.ludicroushooks.item.ItemMotorizedGrappleGun;
import com.techshroom.ludicroushooks.json.MultimapAdapter;
import com.techshroom.ludicroushooks.json.NBTAdapter;
import com.techshroom.ludicroushooks.movement.MotionController;
import com.techshroom.ludicroushooks.proxy.Proxy;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod(modid = Constants.MOD_ID, name = Constants.NAME,
        version = Constants.VERSION, acceptedMinecraftVersions = "[1.10.2]")
public class LudicrousHooks {

    public static final Logger LOGGER = LogManager.getLogger(Constants.MOD_ID);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(NBTTagCompound.class, NBTAdapter.INSTANCE)
            .registerTypeAdapterFactory(MultimapAdapter.INSTANCE).create();

    @SuppressWarnings("serial")
    private static final Type UNSPAWNED_HOOKS_TYPE =
            new TypeToken<Multimap<UUID, NBTTagCompound>>() {}.getType();

    @Instance
    private static LudicrousHooks instance;

    public static LudicrousHooks getInstance() {
        return instance;
    }

    @SidedProxy(clientSide = "com.techshroom.ludicroushooks.proxy.ClientProxy",
            serverSide = "com.techshroom.ludicroushooks.proxy.ServerProxy")
    private static Proxy proxy;

    public static Proxy getProxy() {
        return proxy;
    }

    public static final Item MOTOR_GRAPPLE_GUN = new ItemMotorizedGrappleGun()
            .setCreativeTab(CreativeTabs.TRANSPORTATION)
            .setRegistryName(Constants.MOD_ID, "grapplegun.motor")
            .setUnlocalizedName("grapplegun.motor").setHasSubtypes(true)
            .setMaxStackSize(1);

    public static final Item HANG_GRAPPLE_GUN =
            new ItemHangGrappleGun().setCreativeTab(CreativeTabs.TRANSPORTATION)
                    .setRegistryName(Constants.MOD_ID, "grapplegun.hang")
                    .setUnlocalizedName("grapplegun.hang").setHasSubtypes(true)
                    .setMaxStackSize(1);

    private Path unspawnedHooksSave;
    private Multimap<UUID, NBTTagCompound> unspawnedHooks =
            HashMultimap.create();
    // TODO move to ClientProxy??
    @SideOnly(Side.CLIENT)
    private Multimap<UUID, MotionController<?>> activeMotionControllers =
            HashMultimap.create();

    @EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @EventHandler
    public void onPreInit(FMLPreInitializationEvent event) throws IOException {
        this.unspawnedHooksSave = event.getModConfigurationDirectory().toPath()
                .resolve(Constants.MOD_ID).resolve("unspawnedHooks.json");
        Files.createDirectories(this.unspawnedHooksSave.getParent());

        int id = 0;
        EntityRegistry.registerModEntity(EntityMotorizedHook.class,
                "hook.motor", id++, this, 256, 20, false);
        EntityRegistry.registerModEntity(EntityHangHook.class, "hook.hang",
                id++, this, 256, 20, false);
        DataSerializers.registerSerializer(UUIDDataSerializer.INSTANCE);
        DataSerializers.registerSerializer(Vec3dDataSerializer.INSTANCE);
        DataSerializers.registerSerializer(LongDataSerializer.INSTANCE);

        getProxy().onPreInit(event);

        if (Files.exists(this.unspawnedHooksSave)) {
            try (Reader r = Files.newBufferedReader(this.unspawnedHooksSave)) {
                Multimap<UUID, NBTTagCompound> hooks =
                        GSON.fromJson(r, UNSPAWNED_HOOKS_TYPE);
                checkNotNull(hooks);
                this.unspawnedHooks.putAll(hooks);
            } catch (Exception e) {
                event.getModLog().warn(
                        "Error loading unspawned hooks, backing up and deleting file",
                        e);
                Files.move(this.unspawnedHooksSave,
                        this.unspawnedHooksSave
                                .resolveSibling("unspawnedHooks.json.bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @SubscribeEvent
    public void onItemRegistryReady(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(MOTOR_GRAPPLE_GUN);
        event.getRegistry().register(HANG_GRAPPLE_GUN);
    }

    @EventHandler
    public void onShutdown(FMLServerStoppingEvent event) throws IOException {
        try (Writer w = Files.newBufferedWriter(this.unspawnedHooksSave)) {
            GSON.toJson(this.unspawnedHooks, UNSPAWNED_HOOKS_TYPE, w);
        }
    }

    @SubscribeEvent
    public void onPlayerEntitySpawn(EntityJoinWorldEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        Collection<NBTTagCompound> hooks =
                this.unspawnedHooks.get(event.getEntity().getUniqueID());
        hooks.forEach(h -> {
            Entity hook = EntityList.createEntityFromNBT(h, event.getWorld());
            if (!(hook instanceof EntityHook)) {
                LOGGER.warn("Unspanwed hook created an instance of "
                        + hook.getClass().getName() + " rather than "
                        + EntityHook.class.getName());
            }
            event.getWorld().spawnEntityInWorld(hook);
        });
        hooks.clear();
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void afterNormalPlayerTick(PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !proxy.thisClientIsUUID(event.player.getUniqueID())) {
            return;
        }
        Collection<MotionController<?>> hooks =
                this.activeMotionControllers.get(event.player.getUniqueID());
        if (hooks.isEmpty()) {
            return;
        }
        hooks.forEach(h -> h.updatePlayerMotion(event.player));
    }

    public void addPendingHook(UUID owner, NBTTagCompound compound) {
        this.unspawnedHooks.put(owner, compound);
    }

    public boolean canAttachTo(IBlockState state) {
        // TODO config
        // not liquids
        // full cubes
        // or opaque non-cubes
        return !(state.getBlock() instanceof BlockLiquid)
                && (state.isFullCube() || state.getMaterial().isOpaque());
    }

    @SideOnly(Side.CLIENT)
    public void addActiveHook(UUID owner, EntityHook entityHook) {
        this.activeMotionControllers.put(owner,
                entityHook.createMotionController());
    }

    @SideOnly(Side.CLIENT)
    public void removeActiveHook(UUID owner, EntityHook entityHook) {
        Collection<MotionController<?>> controllers =
                this.activeMotionControllers.get(owner);
        for (Iterator<MotionController<?>> iterator =
                controllers.iterator(); iterator.hasNext();) {
            MotionController<?> motionController = iterator.next();
            if (motionController.getAssociatedHook() == entityHook) {
                iterator.remove();
            }
        }
    }

}

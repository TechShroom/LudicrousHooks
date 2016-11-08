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

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.techshroom.ludicroushooks.LudicrousHooks;
import com.techshroom.ludicroushooks.VecUtil;
import com.techshroom.ludicroushooks.item.ItemGrappleGun;
import com.techshroom.ludicroushooks.movement.MotionController;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class EntityHook extends Entity {

    private static final DataParameter<Boolean> DRAG_PLAYER = EntityDataManager
            .createKey(EntityHook.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Vec3d> ATTACH_POSITION =
            EntityDataManager.createKey(EntityHook.class,
                    Vec3dDataSerializer.INSTANCE);
    private static final DataParameter<Long> TRAVEL_TIME = EntityDataManager
            .createKey(EntityHook.class, LongDataSerializer.INSTANCE);
    private static final DataParameter<UUID> OWNER = EntityDataManager
            .createKey(EntityHook.class, UUIDDataSerializer.INSTANCE);
    private static final long NANOS_TO_TRAVEL_PER_BLOCK =
            TimeUnit.MILLISECONDS.toNanos(1);
    private static final double HOOK_MAX_DIST = 1024.0;

    public static EntityHook shoot(EntityPlayer shooter,
            Function<World, EntityHook> constr) {
        EntityHook hook = constr.apply(shooter.worldObj);
        hook.setOwner(shooter.getUniqueID());
        hook.setLocationAndAngles(shooter.posX,
                shooter.posY + shooter.getEyeHeight(), shooter.posZ,
                shooter.rotationYaw, shooter.rotationPitch);
        hook.calculateAttachmentValues();
        hook.getEntityWorld().spawnEntityInWorld(hook);
        return hook;
    }

    /**
     * Returns the location of the tip of the grapple gun for the firing entity.
     */
    public static Vec3d getGunLocation(Entity firer) {
        return getGunLocation(firer, 1);
    }

    // TODO improve this a lot client side
    // for now we'll just not take advantage.
    public static Vec3d getGunLocation(Entity firer, float partialTicks) {
        Side side = firer.worldObj.isRemote ? Side.CLIENT : Side.SERVER;
        // if (!(firer instanceof EntityPlayer)) {
        Vec3d res;
        if (side == Side.CLIENT) {
            res = firer.getPositionEyes(partialTicks);
        } else {
            res = firer.getPositionVector().addVector(0, firer.getEyeHeight(),
                    0);
        }
        // draw it slightly lower than eye height for good rendering
        // and slightly into the player
        return res.subtract(0, 1, 0).subtract(VecUtil
                .setLength(Vec3d.fromPitchYaw(0, firer.rotationYaw), 0.05));
        // }
        // return null;
    }

    // private static Vec3d getGunLocationServerSide(EntityPlayer firer) {
    //
    // }
    //
    // @SideOnly(Side.CLIENT)
    // private static Vec3d getGunLocationClientSide(EntityPlayer firer) {
    // RenderPlayer rp =
    // (RenderPlayer) Minecraft.getMinecraft().getRenderManager()
    // .getEntityRenderObject((AbstractClientPlayer) firer);
    // ModelRenderer a = rp.getMainModel().bipedLeftArm;
    // }

    private BlockPos hookedBlockPos;
    private long targetNanos = -1;
    private boolean attached;
    private boolean targetExists;
    private EntityPlayer cachedOwner;
    private boolean positionFromSpawn;
    private boolean motionAdded;

    public EntityHook(World worldIn) {
        super(worldIn);
    }

    @Override
    protected void entityInit() {
        this.ignoreFrustumCheck = true;
        this.noClip = true;
        getDataManager().register(DRAG_PLAYER, false);
        getDataManager().register(ATTACH_POSITION, null);
        getDataManager().register(TRAVEL_TIME, 0L);
        getDataManager().register(OWNER, null);
        setSize(0.1f, 0.1f);
    }

    private void calculateAttachmentValues() {
        EntityDataManager dm = getDataManager();
        Pair<Boolean, Vec3d> attachInfo = checkAttachment();
        Vec3d attachPos = attachInfo.getRight();
        this.targetExists = attachInfo.getLeft();
        if (attachPos == null) {
            attachPos = getPositionVector().add(
                    Vec3d.fromPitchYaw(this.rotationPitch, this.rotationYaw)
                            .scale(HOOK_MAX_DIST));
        }
        long travelTime = (long) (NANOS_TO_TRAVEL_PER_BLOCK
                * attachPos.distanceTo(getPositionVector()));
        dm.set(TRAVEL_TIME, travelTime);
        dm.set(ATTACH_POSITION, attachPos);
        this.targetNanos = System.nanoTime() + travelTime;
    }

    private Pair<Boolean, Vec3d> checkAttachment() {
        Vec3d vec = getPositionVector();
        RayTraceResult trace =
                getEntityWorld().rayTraceBlocks(vec,
                        vec.add(Vec3d.fromPitchYaw(this.rotationPitch,
                                this.rotationYaw).scale(HOOK_MAX_DIST)),
                        false, true, false);
        if (trace != null && trace.typeOfHit == Type.BLOCK) {
            IBlockState state =
                    getEntityWorld().getBlockState(trace.getBlockPos());
            this.hookedBlockPos = trace.getBlockPos();
            return Pair.of(LudicrousHooks.getInstance().canAttachTo(state),
                    trace.hitVec);
        }
        return Pair.of(false, null);
    }

    public void setOwner(UUID owner) {
        getDataManager().set(OWNER, owner);
        this.cachedOwner = null;
    }

    public UUID getOwner() {
        return getDataManager().get(OWNER);
    }

    public EntityPlayer getOwnerEntity() {
        if (this.cachedOwner == null && getOwner() != null) {
            this.cachedOwner =
                    getEntityWorld().getPlayerEntityByUUID(getOwner());
        }
        return this.cachedOwner;
    }

    public boolean isAttached() {
        return this.attached;
    }

    public void setAttached(boolean attached) {
        this.attached = attached;
        getDataManager().set(DRAG_PLAYER, attached);
    }

    /**
     * Attach position is the final position of the entity
     */
    public Vec3d getAttachPosition() {
        return getDataManager().get(ATTACH_POSITION);
    }

    /**
     * Pull position is the place that the rope attaches to, and therefore where
     * the player is pulled towards.
     */
    public Vec3d getPullPosition() {
        return getAttachPosition().add(VecUtil.setLength(getLook(1.0F), -0.25));
    }

    public long getTravelTime() {
        return getDataManager().get(TRAVEL_TIME);
    }

    // Specialized client-only version
    @SideOnly(Side.CLIENT)
    public long getTargetNanos() {
        return this.targetNanos;
    }

    @Override
    public void setDead() {
        super.setDead();
        if (getOwner() != null) {
            if (getEntityWorld().isRemote) {
                LudicrousHooks.getInstance().removeActiveHook(getOwner(), this);
            } else {
                returnToStack();
            }
        }

    }

    private void returnToStack() {
        EntityPlayer owner = getOwnerEntity();
        if (owner == null) {
            return;
        }
        // Check for holding
        if (returnToStack(owner.getHeldItem(EnumHand.MAIN_HAND))) {
            return;
        }
        if (returnToStack(owner.getHeldItem(EnumHand.OFF_HAND))) {
            return;
        }
        InventoryPlayer inv = owner.inventory;
        // Check inventory
        for (int i = 0; i < inv.mainInventory.length; i++) {
            if (returnToStack(inv.mainInventory[i])) {
                return;
            }
        }
    }

    private boolean returnToStack(ItemStack stack) {
        if (stack != null && stack.getItem() == LudicrousHooks.MOTOR_GRAPPLE_GUN
                && stack.getMetadata() == ItemGrappleGun.SHOT) {
            stack.setItemDamage(ItemGrappleGun.IN_HAND);
            stack.setTagCompound(null);
            return true;
        }
        return false;
    }

    @Override
    public void onEntityUpdate() {
        UUID owner = getOwner();
        // update position to owner
        if (owner != null) {
            EntityPlayer e = getOwnerEntity();
            if (e == null) {
                if (!getEntityWorld().isRemote) {
                    NBTTagCompound data = new NBTTagCompound();
                    writeToNBT(data);
                    LudicrousHooks.getInstance().addPendingHook(owner, data);
                }
                setDead();
                return;
            }
            // Note: this position is not intended to sync
            this.lastTickPosX = this.prevPosX = this.posX = e.posX;
            this.lastTickPosY = this.prevPosY = this.posY = e.posY;
            this.lastTickPosZ = this.prevPosZ = this.posZ = e.posZ;
        }
        if (!getEntityWorld().isRemote) {
            if (owner == null) {
                setDead();
                return;
            }
            if (!isAttached()) {
                if (System.nanoTime() >= this.targetNanos) {
                    if (!this.targetExists) {
                        killWithEffects();
                    } else {
                        setAttached(true);
                    }
                }
            }
        } else {
            long travelTime;
            if (this.targetNanos == -1 && (travelTime = getTravelTime()) != 0) {
                this.targetNanos = System.nanoTime() + travelTime;
            }
            if (getDataManager().get(DRAG_PLAYER)
                    && LudicrousHooks.getProxy().thisClientIsUUID(owner)) {
                if (!this.motionAdded) {
                    // Initialize connection here
                    this.motionAdded = true;
                    LudicrousHooks.getInstance().addActiveHook(owner, this);
                }
            } else if (owner != null) {
                LudicrousHooks.getInstance().removeActiveHook(owner, this);
            }
        }
    }

    public void killWithEffects() {
        if (this.hookedBlockPos != null) {
            WorldServer w = (WorldServer) getEntityWorld();
            // 2001 -> block break event
            w.playEvent(2001, this.hookedBlockPos,
                    Block.getStateId(w.getBlockState(this.hookedBlockPos)));
        }
        setDead();
    }

    // #renderalways #noexceptions
    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRender3d(double x, double y, double z) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distance) {
        return true;
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        if (compound.hasKey("attachPos", NBT.TAG_LIST)) {
            NBTTagList attachPos =
                    compound.getTagList("attachPos", NBT.TAG_DOUBLE);
            if (attachPos.tagCount() == 3) {
                getDataManager().set(ATTACH_POSITION,
                        new Vec3d(attachPos.getDoubleAt(0),
                                attachPos.getDoubleAt(1),
                                attachPos.getDoubleAt(2)));
            }
        } else if (compound.hasKey("attachPos", NBT.TAG_STRING)) {
            if ("spawn".equals(compound.getString("attachPos"))) {
                // This is for supporting summon in command blocks...delay until
                // position set
                this.positionFromSpawn = true;
            }
        }
        if (compound.hasKey("owner", NBT.TAG_STRING)) {
            setOwner(UUID.fromString(compound.getString("owner")));
        } else {
            setOwner(compound.getUniqueId("owner"));
        }
        boolean attached = compound.getBoolean("attached");
        long travelTime = 0;
        if (compound.hasKey("travelTime")) {
            travelTime = compound.getLong("travelTime");
        }
        getDataManager().set(TRAVEL_TIME, travelTime);
        this.targetNanos = System.nanoTime() + travelTime;
        setAttached(attached);
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        Vec3d attachPos = getAttachPosition();
        if (attachPos != null) {
            NBTTagList nbtAttach = new NBTTagList();
            nbtAttach.appendTag(new NBTTagDouble(attachPos.xCoord));
            nbtAttach.appendTag(new NBTTagDouble(attachPos.yCoord));
            nbtAttach.appendTag(new NBTTagDouble(attachPos.zCoord));
            compound.setTag("attachPos", nbtAttach);
        }
        compound.setUniqueId("owner", getOwner());
        compound.setBoolean("attached", isAttached());
        compound.setLong("travelTime", getTravelTime());
    }

    @Override
    public void setLocationAndAngles(double x, double y, double z, float yaw,
            float pitch) {
        super.setLocationAndAngles(x, y, z, yaw, pitch);
        if (this.positionFromSpawn) {
            this.positionFromSpawn = false;
            getDataManager().set(ATTACH_POSITION, getPositionVector());
        }
    }

    @SideOnly(Side.CLIENT)
    public abstract MotionController<?> createMotionController();

}

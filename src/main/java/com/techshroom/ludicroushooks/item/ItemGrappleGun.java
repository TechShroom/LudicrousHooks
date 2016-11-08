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
package com.techshroom.ludicroushooks.item;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.techshroom.ludicroushooks.entity.EntityHook;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public abstract class ItemGrappleGun extends Item {

    public static final int IN_HAND = 0;
    public static final int SHOT = 1;

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack itemStackIn,
            World worldIn, EntityPlayer playerIn, EnumHand hand) {
        if (itemStackIn.getItemDamage() == IN_HAND) {
            return onRightClickInHand(itemStackIn, worldIn, playerIn);
        } else if (itemStackIn.getItemDamage() == SHOT) {
            return onRightClickShot(itemStackIn, worldIn, playerIn);
        }
        return ActionResult.newResult(EnumActionResult.PASS, itemStackIn);
    }

    private ActionResult<ItemStack> onRightClickInHand(ItemStack itemStackIn,
            World worldIn, EntityPlayer playerIn) {
        if (!worldIn.isRemote) {
            EntityHook hook = EntityHook.shoot(playerIn, getHookConstructor());
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setUniqueId("hook", hook.getUniqueID());
            itemStackIn.setTagCompound(nbt);
            itemStackIn.setItemDamage(SHOT);
            return ActionResult.newResult(EnumActionResult.SUCCESS,
                    itemStackIn);
        }
        // assume it got shot ...
        return ActionResult.newResult(EnumActionResult.SUCCESS, itemStackIn);
    }

    protected abstract Function<World, EntityHook> getHookConstructor();

    private ActionResult<ItemStack> onRightClickShot(ItemStack itemStackIn,
            World worldIn, EntityPlayer playerIn) {
        ActionResult<ItemStack> result =
                ActionResult.newResult(EnumActionResult.SUCCESS, itemStackIn);
        if (!worldIn.isRemote) {
            NBTTagCompound tagCompound = itemStackIn.getTagCompound();
            itemStackIn.setTagCompound(null);
            itemStackIn.setItemDamage(IN_HAND);
            if (tagCompound == null) {
                return result;
            }
            UUID hookId = tagCompound.getUniqueId("hook");
            if (hookId == null) {
                return result;
            }
            List<EntityHook> hooks = worldIn.getEntities(EntityHook.class,
                    h -> h.getUniqueID().equals(hookId));
            hooks.forEach(EntityHook::killWithEffects);
            return result;
        }
        // assume it got retracted ...
        return result;
    }

}

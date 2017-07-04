/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.lib.block;

import java.util.HashSet;

import Reika.RotaryCraft.API.Power.IAdvancedShaftPowerReceiver;
import Reika.RotaryCraft.API.Power.IShaftPowerInputCaller;
import Reika.RotaryCraft.API.Power.ShaftPowerInputManager;
import buildcraft.api.core.BCLog;
import io.netty.buffer.ByteBuf;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

import net.minecraftforge.common.util.ForgeDirection;

import buildcraft.BuildCraftCore;
import buildcraft.api.core.ISerializable;
import buildcraft.api.tiles.IControllable;
import buildcraft.core.DefaultProps;
import buildcraft.core.lib.TileBuffer;
import buildcraft.core.lib.network.Packet;
import buildcraft.core.lib.network.PacketTileUpdate;
import buildcraft.core.lib.utils.Utils;

/**
 * For future maintainers: This class intentionally does not implement
 * just every interface out there. For some of them (such as IControllable),
 * we expect the tiles supporting it to implement it - but TileBuildCraft
 * provides all the underlying functionality to stop code repetition.
 */
public abstract class TileBuildCraft extends TileEntity implements IShaftPowerInputCaller, ISerializable {
    protected TileBuffer[] cache;
    protected HashSet<EntityPlayer> guiWatchers = new HashSet<EntityPlayer>();
    protected IControllable.Mode mode;

    private boolean init = false;
    private String owner = "[BuildCraft]";
    private ShaftPowerInputManager shaftPowerInputManager;

    public String getOwner() {
        return owner;
    }

    public void addGuiWatcher(EntityPlayer player) {
        if (!guiWatchers.contains(player)) {
            guiWatchers.add(player);
        }
    }

    public void removeGuiWatcher(EntityPlayer player) {
        if (guiWatchers.contains(player)) {
            guiWatchers.remove(player);
        }
    }

    @Override
    public void updateEntity() {
        if (!init && !isInvalid()) {
            initialize();
            init = true;
        }

        if (shaftPowerInputManager != null) {
            shaftPowerInputManager.update();
        }
    }

    public void initialize() {

    }

    @Override
    public void validate() {
        super.validate();
        cache = null;
    }

    @Override
    public void invalidate() {
        init = false;
        super.invalidate();
        cache = null;
    }

    public void onBlockPlacedBy(EntityLivingBase entity, ItemStack stack) {
        if (entity instanceof EntityPlayer) {
            owner = ((EntityPlayer) entity).getDisplayName();
        }
    }

    public void destroy() {
        cache = null;
    }

    public void sendNetworkUpdate() {
        if (worldObj != null && !worldObj.isRemote) {
            BuildCraftCore.instance.sendToPlayers(getPacketUpdate(), worldObj,
                    xCoord, yCoord, zCoord, getNetworkUpdateRange());
        }
    }

    protected int getNetworkUpdateRange() {
        return DefaultProps.NETWORK_UPDATE_RANGE;
    }

    public void writeData(ByteBuf stream) {
        if (shaftPowerInputManager != null)
        {
            stream.writeInt(shaftPowerInputManager.getTorque());
            stream.writeInt(shaftPowerInputManager.getOmega());
            stream.writeBoolean(shaftPowerInputManager.hasMismatchedInputs());

            for (int stageIndex = 0; stageIndex < getStageCount(); stageIndex++)
            {
                stream.writeInt(getMinTorque(stageIndex));
                stream.writeInt(getMinOmega(stageIndex));
                stream.writeLong(getMinPower(stageIndex));
            }
        }
    }

    public void readData(ByteBuf stream) {
        if (shaftPowerInputManager != null)
        {
            shaftPowerInputManager.setState(stream.readInt(), stream.readInt(), stream.readBoolean());

            for (int stageIndex = 0; stageIndex < getStageCount(); stageIndex++)
            {
                shaftPowerInputManager.setMinTorque(stageIndex, stream.readInt());
                shaftPowerInputManager.setMinOmega(stageIndex, stream.readInt());
                shaftPowerInputManager.setMinPower(stageIndex, stream.readLong());
            }
        }
    }

    public Packet getPacketUpdate() {
        return new PacketTileUpdate(this);
    }

    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        return Utils.toPacket(getPacketUpdate(), 0);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setString("owner", owner);
        if (mode != null) {
            nbt.setByte("lastMode", (byte) mode.ordinal());
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.hasKey("owner")) {
            owner = nbt.getString("owner");
        }
        if (nbt.hasKey("lastMode")) {
            mode = IControllable.Mode.values()[nbt.getByte("lastMode")];
        }
    }

    @Override
    public int hashCode() {
        return (xCoord * 37 + yCoord) * 37 + zCoord;
    }

    @Override
    public boolean equals(Object cmp) {
        return this == cmp;
    }

    public ShaftPowerInputManager getBattery() {
        return shaftPowerInputManager;
    }

    protected void setBattery(ShaftPowerInputManager shaftPowerInputManager) {
        this.shaftPowerInputManager = shaftPowerInputManager;
    }

    public Block getBlock(ForgeDirection side) {
        if (cache == null) {
            cache = TileBuffer.makeBuffer(worldObj, xCoord, yCoord, zCoord, false);
        }
        return cache[side.ordinal()].getBlock();
    }

    public TileEntity getTile(ForgeDirection side) {
        if (cache == null) {
            cache = TileBuffer.makeBuffer(worldObj, xCoord, yCoord, zCoord, false);
        }
        return cache[side.ordinal()].getTile();
    }

    public IControllable.Mode getControlMode() {
        return mode;
    }

    public void setControlMode(IControllable.Mode mode) {
        this.mode = mode;
    }

    /* Rotary Power */

    @Override
    public void onPowerChange(ShaftPowerInputManager shaftPowerInputManager) {
        sendNetworkUpdate();
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public boolean addPower(int addTorque, int addOmega, long addPower, ForgeDirection inputDirection) {
        return shaftPowerInputManager != null && shaftPowerInputManager.addPower(addTorque, addOmega, addPower, inputDirection);
    }

    @Override
    public int getStageCount() {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getStageCount() : 0;
    }

    @Override
    public void setIORenderAlpha(int i) {
        if (shaftPowerInputManager != null) shaftPowerInputManager.setIORenderAlpha(i);
    }

    @Override
    public boolean canReadFrom(ForgeDirection forgeDirection) {
        return true;
    }

    @Override
    public boolean hasMismatchedInputs() {
        return shaftPowerInputManager != null && shaftPowerInputManager.hasMismatchedInputs();
    }

    @Override
    public boolean isReceiving() {
        return shaftPowerInputManager != null && shaftPowerInputManager.isReceiving();
    }

    @Override
    public int getMinTorque(int stageIndex) {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getMinTorque(stageIndex) : 1;
    }

    @Override
    public int getMinOmega(int stageIndex) {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getMinOmega(stageIndex) : 1;
    }

    @Override
    public long getMinPower(int stageIndex) {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getMinPower(stageIndex) : 1;
    }

    @Override
    public long getPower() {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getPower() : 0;
    }

    @Override
    public int getOmega() {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getOmega() : 0;
    }

    @Override
    public int getTorque() {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getTorque() : 0;
    }

    @Override
    public String getName() {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getName() : "[BuildCraft]";
    }

    @Override
    public int getIORenderAlpha() {
        return shaftPowerInputManager != null ? shaftPowerInputManager.getIORenderAlpha() : 0;
    }
}

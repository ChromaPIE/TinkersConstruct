package tconstruct.tools.logic;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import mantle.blocks.abstracts.InventoryLogic;
import tconstruct.tools.inventory.CraftingStationContainer;
import tconstruct.util.config.PHConstruct;

public class CraftingStationLogic extends InventoryLogic implements ISidedInventory {

    public ForgeDirection chestDirection = ForgeDirection.UNKNOWN;
    public int chestSize;
    public WeakReference<IInventory> chest;
    public WeakReference<IInventory> doubleChest;
    public WeakReference<IInventory> patternChest;
    public WeakReference<IInventory> furnace;
    public boolean tinkerTable;
    public boolean stencilTable;
    public boolean doubleFirst;

    public int invRows, invColumns, slotCount;

    // Multi-chest support
    public List<WeakReference<IInventory>> multiChests = new ArrayList<>();
    public List<ForgeDirection> multiChestDirections = new ArrayList<>();
    public List<Integer> multiChestSizes = new ArrayList<>();
    public boolean isMultiChest = false;

    public CraftingStationLogic() {
        super(10); // 9 for crafting, 1 for output
    }

    @Override
    public boolean canDropInventorySlot(int slot) {
        return slot != 0;
    }

    @Override
    public ItemStack decrStackSize(int slot, int quantity) {
        if (slot == 0) {
            for (int i = 1; i < getSizeInventory(); i++) decrStackSize(i, 1);
        }
        return super.decrStackSize(slot, quantity);
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return isUseableByPlayer(player, this.getInventories()) && super.isUseableByPlayer(player);
    }

    @Override
    public Container getGuiContainer(InventoryPlayer inventoryplayer, World world, int x, int y, int z) {
        chest = null;
        chestSize = 0;
        slotCount = 0;
        chestDirection = ForgeDirection.UNKNOWN;
        doubleChest = null;
        patternChest = null;
        furnace = null;
        tinkerTable = false;

        multiChests.clear();
        multiChestDirections.clear();
        multiChestSizes.clear();
        isMultiChest = false;

        List<TileEntity> validChests = new ArrayList<>();
        List<ForgeDirection> validDirections = new ArrayList<>();

        for (final ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            final int xPos = x + dir.offsetX, yPos = y + dir.offsetY, zPos = z + dir.offsetZ;
            final TileEntity tile = world.getTileEntity(xPos, yPos, zPos);
            if (!(tile instanceof IInventory inv) || (tile instanceof CraftingStationLogic)
                    || isBlacklisted(tile.getClass()))
                continue;

            if (patternChest == null && tile instanceof PatternChestLogic) {
                patternChest = new WeakReference<>(inv);
                continue;
            } else if (furnace == null && (tile instanceof TileEntityFurnace || tile instanceof FurnaceLogic)) {
                furnace = new WeakReference<>(inv);
                continue;
            } else if (!tinkerTable && tile instanceof ToolStationLogic) {
                tinkerTable = true;
                continue;
            }

            if (tile instanceof ISidedInventory sidedIvn
                    && sidedIvn.getAccessibleSlotsFromSide(dir.getOpposite().ordinal()).length == 0)
                continue;

            if (inv.isUseableByPlayer(inventoryplayer.player)) {
                validChests.add(tile);
                validDirections.add(dir);
            }
        }

        if (validChests.size() == 1) {
            // Single chest mode - original logic
            TileEntity tile = validChests.get(0);
            IInventory inv = (IInventory) tile;
            ForgeDirection dir = validDirections.get(0);

            chest = new WeakReference<>(inv);
            chestDirection = dir;
            invColumns = 6;
            chestSize = tile instanceof ISidedInventory sidedIvn
                    ? sidedIvn.getAccessibleSlotsFromSide(dir.getOpposite().ordinal()).length
                    : inv.getSizeInventory();

            if (tile instanceof TileEntityChest tileChest) {
                if (tileChest.adjacentChestZPos != null) {
                    doubleChest = new WeakReference<>(tileChest.adjacentChestZPos);
                    doubleFirst = false;
                } else if (tileChest.adjacentChestZNeg != null) {
                    doubleChest = new WeakReference<>(tileChest.adjacentChestZNeg);
                    doubleFirst = true;
                } else if (tileChest.adjacentChestXPos != null) {
                    doubleChest = new WeakReference<>(tileChest.adjacentChestXPos);
                    doubleFirst = false;
                } else if (tileChest.adjacentChestXNeg != null) {
                    doubleChest = new WeakReference<>(tileChest.adjacentChestXNeg);
                    doubleFirst = true;
                }
            }
            slotCount = chestSize * (doubleChest != null ? 2 : 1);
            invRows = (int) Math.ceil((double) slotCount / invColumns);
        } else if (validChests.size() > 1) {
            // Multi-chest mode
            isMultiChest = true;
            invColumns = 6;
            slotCount = 0;

            for (int i = 0; i < validChests.size(); i++) {
                TileEntity tile = validChests.get(i);
                IInventory inv = (IInventory) tile;
                ForgeDirection dir = validDirections.get(i);

                multiChests.add(new WeakReference<>(inv));
                multiChestDirections.add(dir);

                int size = tile instanceof ISidedInventory sidedIvn
                        ? sidedIvn.getAccessibleSlotsFromSide(dir.getOpposite().ordinal()).length
                        : inv.getSizeInventory();
                multiChestSizes.add(size);
                slotCount += size;
            }

            invRows = (int) Math.ceil((double) slotCount / invColumns);

            // Set first chest as primary for compatibility
            if (!multiChests.isEmpty()) {
                chest = multiChests.get(0);
                chestDirection = multiChestDirections.get(0);
            }
        }

        return new CraftingStationContainer(inventoryplayer, this, x, y, z);
    }

    private boolean isBlacklisted(Class<? extends TileEntity> clazz) {
        return PHConstruct.craftingStationBlacklist.contains(clazz.getName());
    }

    public boolean isDoubleChest() {
        return this.doubleChest != null;
    }

    public IInventory getFirstInventory() {
        if (doubleFirst && doubleChest != null) {
            return doubleChest.get();
        } else {
            return chest != null ? chest.get() : null;
        }
    }

    public IInventory getSecondInventory() {
        if (!isDoubleChest()) return null;

        if (doubleFirst) {
            return chest.get();
        } else {
            return doubleChest == null ? null : doubleChest.get();
        }
    }

    @Override
    protected String getDefaultName() {
        return "crafters.CraftingStation";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static boolean isUseableByPlayer(EntityPlayer player, WeakReference[] inventories) {
        for (WeakReference<IInventory> ref : inventories) {
            if (ref != null) {
                IInventory inv = ref.get();
                if (inv != null && !inv.isUseableByPlayer(player)) return false;
            }
        }

        return true;
    }

    @SuppressWarnings("rawtypes")
    public WeakReference[] getInventories() {
        return new WeakReference[] { this.chest, this.doubleChest, this.patternChest, this.furnace };
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int var1) {
        return new int[] {};
    }

    @Override
    public boolean canInsertItem(int i, ItemStack itemstack, int j) {
        return false;
    }

    @Override
    public boolean canExtractItem(int i, ItemStack itemstack, int j) {
        return false;
    }

    @Override
    public String getInventoryName() {
        return getDefaultName();
    }

    @Override
    public void openInventory() {
        // TODO Auto-generated method stub

    }

    @Override
    public void closeInventory() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean canUpdate() {
        return false;
    }
}

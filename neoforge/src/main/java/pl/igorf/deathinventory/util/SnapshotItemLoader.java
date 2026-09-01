package pl.igorf.deathinventory.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class SnapshotItemLoader {
    private SnapshotItemLoader() {
    }

    public static void applyToPlayer(CompoundTag snapshot, ServerPlayer player) {
        if (!snapshot.contains("Items", Tag.TAG_LIST)) {
            return;
        }

        HolderLookup.Provider registryAccess = player.registryAccess();
        Inventory inventory = player.getInventory();
        inventory.clearContent();

        ListTag items = snapshot.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag tag = items.getCompound(i);
            int slot = tag.getByte("Slot") & 255;
            ItemStack stack = ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }

            if (slot >= 0 && slot < 36) {
                inventory.setItem(slot, stack);
            } else if (slot >= 100 && slot < 104) {
                EquipmentSlot equipmentSlot = armorSlot(slot - 100);
                if (equipmentSlot != null) {
                    player.setItemSlot(equipmentSlot, stack);
                }
            } else if (slot == 150) {
                player.setItemSlot(EquipmentSlot.OFFHAND, stack);
            }
        }

        if (snapshot.contains("SelectedSlot")) {
            inventory.selected = snapshot.getInt("SelectedSlot");
        }
    }

    public static void loadIntoContainer(ListTag items, Container container, HolderLookup.Provider registryAccess) {
        for (int i = 0; i < items.size(); i++) {
            CompoundTag tag = items.getCompound(i);
            int slot = tag.getByte("Slot") & 255;
            ItemStack stack = ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }

            int containerSlot = mapInventorySlotToContainer(slot);
            if (containerSlot >= 0 && containerSlot < container.getContainerSize()) {
                container.setItem(containerSlot, stack);
            }
        }
    }

    public static void loadIntoContainer(CompoundTag snapshot, Container container, HolderLookup.Provider registryAccess) {
        if (!snapshot.contains("Items", Tag.TAG_LIST)) {
            return;
        }
        loadIntoContainer(snapshot.getList("Items", Tag.TAG_COMPOUND), container, registryAccess);
    }

    private static int mapInventorySlotToContainer(int slot) {
        if (slot >= 0 && slot < 36) {
            return slot;
        }
        if (slot >= 100 && slot < 104) {
            return 36 + (slot - 100);
        }
        if (slot == 150) {
            return 40;
        }
        return -1;
    }

    private static EquipmentSlot armorSlot(int index) {
        return switch (index) {
            case 0 -> EquipmentSlot.FEET;
            case 1 -> EquipmentSlot.LEGS;
            case 2 -> EquipmentSlot.CHEST;
            case 3 -> EquipmentSlot.HEAD;
            default -> null;
        };
    }
}

package pl.igorf.deathinventory.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import pl.igorf.deathinventory.InventoryBackupManager;
import pl.igorf.deathinventory.ModMenus;

import java.util.UUID;

public class BackupViewMenu extends AbstractContainerMenu {
    public static final int BACKUP_SLOTS = 41;

    private final Container backupContainer;
    private final UUID targetPlayerId;
    private final String backupId;
    private final String targetPlayerName;
    private final int backupNumber;
    private final CompoundTag snapshot;
    private final InventoryBackupManager manager;

    public BackupViewMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, UUID.randomUUID(), "", "", 0, null, null);
    }

    public BackupViewMenu(int containerId, Inventory playerInventory, UUID targetPlayerId, String backupId,
                          String targetPlayerName, int backupNumber, CompoundTag snapshot,
                          InventoryBackupManager manager) {
        super(ModMenus.BACKUP_VIEW.get(), containerId);
        this.targetPlayerId = targetPlayerId;
        this.backupId = backupId;
        this.targetPlayerName = targetPlayerName;
        this.backupNumber = backupNumber;
        this.snapshot = snapshot;
        this.manager = manager;
        this.backupContainer = new SimpleContainer(BACKUP_SLOTS);
        loadBackupItems();

        addArmorSlots(8, 18);
        addMainInventory(62, 18);
        addHotbar(62, 74);

        addPlayerInventory(playerInventory, 8, 126);
        addPlayerHotbar(playerInventory, 8, 184);
    }

    public static BackupViewMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        UUID targetId = buf.readUUID();
        String backupId = buf.readUtf();
        String targetName = buf.readUtf();
        int backupNumber = buf.readInt();
        return new BackupViewMenu(containerId, playerInventory, targetId, backupId, targetName, backupNumber, null, null);
    }

    private void loadBackupItems() {
        if (snapshot == null) {
            return;
        }

        ListTag items = snapshot.getList("Items", Tag.TAG_COMPOUND);
        net.minecraft.world.entity.player.Inventory tempInventory = new net.minecraft.world.entity.player.Inventory(null);
        tempInventory.load(items);

        for (int i = 0; i < 36; i++) {
            backupContainer.setItem(i, tempInventory.getItem(i).copy());
        }

        for (int i = 0; i < 4; i++) {
            backupContainer.setItem(36 + i, tempInventory.armor.get(i).copy());
        }

        backupContainer.setItem(40, tempInventory.offhand.get(0).copy());
    }

    private void addArmorSlots(int left, int top) {
        addSlot(new ReadOnlySlot(backupContainer, 36, left, top));
        addSlot(new ReadOnlySlot(backupContainer, 37, left, top + 18));
        addSlot(new ReadOnlySlot(backupContainer, 38, left, top + 36));
        addSlot(new ReadOnlySlot(backupContainer, 39, left, top + 54));
        addSlot(new ReadOnlySlot(backupContainer, 40, left + 22, top + 36));
    }

    private void addMainInventory(int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new ReadOnlySlot(backupContainer, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
    }

    private void addHotbar(int left, int top) {
        for (int col = 0; col < 9; col++) {
            addSlot(new ReadOnlySlot(backupContainer, col, left + col * 18, top));
        }
    }

    private void addPlayerInventory(Inventory playerInventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, left + col * 18, top + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory, int left, int top) {
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, left + col * 18, top));
        }
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < BACKUP_SLOTS) {
            return;
        }

        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0 && player instanceof ServerPlayer serverPlayer) {
            tryRestore(serverPlayer);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < BACKUP_SLOTS) {
            return ItemStack.EMPTY;
        }

        ItemStack movedStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            movedStack = stack.copy();
            if (index < BACKUP_SLOTS + 27) {
                if (!this.moveItemStackTo(stack, BACKUP_SLOTS + 27, BACKUP_SLOTS + 36, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, BACKUP_SLOTS, BACKUP_SLOTS + 27, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return movedStack;
    }

    private void tryRestore(ServerPlayer operator) {
        if (!operator.hasPermissions(2) || snapshot == null || manager == null) {
            return;
        }

        MinecraftServer server = operator.getServer();
        if (server == null) {
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetPlayerId);
        if (target == null) {
            operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Gracz " + targetPlayerName + " musi byc online, aby przywrocic ekwipunek."
            ));
            return;
        }

        this.manager.applySnapshot(target, snapshot);
        operator.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "Przywrocono ekwipunek gracza " + targetPlayerName + " z backupu #" + backupNumber + "."
        ));
        target.sendSystemMessage(net.minecraft.network.chat.Component.literal("Operator przywrocil Twoj ekwipunek i poziom doswiadczenia z backupu."));
        operator.closeContainer();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public String getTargetPlayerName() {
        return targetPlayerName;
    }

    public String getBackupId() {
        return backupId;
    }

    public int getBackupNumber() {
        return backupNumber;
    }

    public UUID getTargetPlayerId() {
        return targetPlayerId;
    }
}

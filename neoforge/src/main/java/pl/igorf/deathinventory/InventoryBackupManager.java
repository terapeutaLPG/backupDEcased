package pl.igorf.deathinventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class InventoryBackupManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_BACKUPS_PER_PLAYER = 20;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        private int count;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "DeathInvBackup-IO-" + (++count));
            thread.setDaemon(true);
            return thread;
        }
    });

    public void shutdown() {
        ioExecutor.shutdown();
    }

    public void saveOnDeath(ServerPlayer player, DamageSource damageSource) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        long timestamp = Instant.now().toEpochMilli();
        String backupId = String.valueOf(timestamp);
        String playerName = player.getName().getString();
        UUID playerId = player.getUUID();
        CompoundTag snapshot = createSnapshot(player, damageSource, timestamp);
        Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path playerDir = worldRoot.resolve("deathinventorybackup").resolve(playerId.toString());

        ioExecutor.execute(() -> {
            try {
                Files.createDirectories(playerDir);
                Path target = playerDir.resolve(backupId + ".dat");
                NbtIo.writeCompressed(snapshot, target);
                pruneOldBackups(playerDir);
                LOGGER.info("Zapisano ekwipunek gracza {} ({})", playerName, backupId);
                server.execute(() -> notifyBackupSaved(server, playerId, playerName, backupId));
            } catch (IOException exception) {
                LOGGER.error("Nie udalo sie zapisac ekwipunku gracza {}", playerName, exception);
            }
        });
    }

    private void notifyBackupSaved(MinecraftServer server, UUID playerId, String playerName, String backupId) {
        int backupNumber = findBackupIndex(server, playerId, backupId);
        net.minecraft.network.chat.Component message = buildBackupCreatedMessage(playerName, backupNumber, backupId);

        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(message);
        }

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online.hasPermissions(2) && !online.getUUID().equals(playerId)) {
                online.sendSystemMessage(message);
            }
        }
    }

    private int findBackupIndex(MinecraftServer server, UUID playerId, String backupId) {
        List<BackupInfo> backups = listBackups(server, playerId);
        for (int i = 0; i < backups.size(); i++) {
            if (backups.get(i).id().equals(backupId)) {
                return i + 1;
            }
        }
        return backups.isEmpty() ? 1 : 1;
    }

    private net.minecraft.network.chat.Component buildBackupCreatedMessage(String playerName, int backupNumber,
                                                                             String backupId) {
        String quotedName = playerName.indexOf(' ') >= 0 ? "\"" + playerName + "\"" : playerName;
        return net.minecraft.network.chat.Component.literal("[Backup] ")
                .withStyle(net.minecraft.ChatFormatting.GOLD)
                .append(net.minecraft.network.chat.Component.literal("Zapisano ekwipunek i XP gracza ")
                        .withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal(playerName)
                        .withStyle(net.minecraft.ChatFormatting.YELLOW))
                .append(net.minecraft.network.chat.Component.literal(" | #").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal(String.valueOf(backupNumber))
                        .withStyle(net.minecraft.ChatFormatting.AQUA))
                .append(net.minecraft.network.chat.Component.literal(" | ID ").withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal(backupId)
                        .withStyle(net.minecraft.ChatFormatting.DARK_AQUA))
                .append(net.minecraft.network.chat.Component.literal(" | ")
                        .withStyle(net.minecraft.ChatFormatting.GRAY))
                .append(net.minecraft.network.chat.Component.literal("[Lista]")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY
                                .withColor(net.minecraft.ChatFormatting.GREEN)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "invbackup list " + quotedName))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                        net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                        net.minecraft.network.chat.Component.literal("Pokaz backupy tego gracza")))));
    }

    public List<BackupInfo> listBackups(MinecraftServer server, UUID playerId) {
        Path playerDir = getPlayerDir(server, playerId);
        List<BackupInfo> backups = new ArrayList<>();

        if (!Files.isDirectory(playerDir)) {
            return backups;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerDir, "*.dat")) {
            for (Path path : stream) {
                backups.add(readBackupInfo(path));
            }
        } catch (IOException exception) {
            LOGGER.error("Nie udalo sie odczytac listy backupow dla {}", playerId, exception);
        }

        backups.sort(Comparator.comparingLong(BackupInfo::timestamp).reversed());
        return backups;
    }

    public Optional<CompoundTag> loadBackup(MinecraftServer server, UUID playerId, String backupId) {
        Path path = getPlayerDir(server, playerId).resolve(backupId + ".dat");
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        try {
            return Optional.of(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
        } catch (IOException exception) {
            LOGGER.error("Nie udalo sie wczytac backupu {}", path, exception);
            return Optional.empty();
        }
    }

    public Optional<BackupInfo> getLatestBackup(MinecraftServer server, UUID playerId) {
        List<BackupInfo> backups = listBackups(server, playerId);
        if (backups.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(backups.get(0));
    }

    public Optional<BackupEntry> getBackupByIndex(MinecraftServer server, UUID playerId, int index) {
        List<BackupInfo> backups = listBackups(server, playerId);
        if (index < 1 || index > backups.size()) {
            return Optional.empty();
        }

        BackupInfo info = backups.get(index - 1);
        return loadBackup(server, playerId, info.id()).map(snapshot -> new BackupEntry(info, snapshot));
    }

    public boolean deleteBackup(MinecraftServer server, UUID playerId, String backupId) {
        Path path = getPlayerDir(server, playerId).resolve(backupId + ".dat");
        try {
            return Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOGGER.error("Nie udalo sie usunac backupu {}", path, exception);
            return false;
        }
    }

    public void applySnapshot(ServerPlayer player, CompoundTag snapshot) {
        Inventory inventory = player.getInventory();
        inventory.clearContent();

        ListTag items = snapshot.getList("Items", Tag.TAG_COMPOUND);
        inventory.load(items);

        if (snapshot.contains("SelectedSlot", Tag.TAG_INT)) {
            inventory.selected = snapshot.getInt("SelectedSlot");
        }

        syncEquippedItems(player);
        applyExperience(player, snapshot);

        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private void applyExperience(ServerPlayer player, CompoundTag snapshot) {
        if (!snapshot.contains("XpLevel", Tag.TAG_INT)) {
            return;
        }

        int level = snapshot.getInt("XpLevel");
        float progress = snapshot.contains("XpP", Tag.TAG_FLOAT) ? snapshot.getFloat("XpP") : 0.0F;
        int total = snapshot.contains("XpTotal", Tag.TAG_INT) ? snapshot.getInt("XpTotal") : 0;

        player.experienceLevel = level;
        player.experienceProgress = progress;
        player.totalExperience = total;
        player.connection.send(new ClientboundSetExperiencePacket(progress, total, level));
    }

    private void syncEquippedItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        player.setItemSlot(EquipmentSlot.FEET, inventory.armor.get(0).copy());
        player.setItemSlot(EquipmentSlot.LEGS, inventory.armor.get(1).copy());
        player.setItemSlot(EquipmentSlot.CHEST, inventory.armor.get(2).copy());
        player.setItemSlot(EquipmentSlot.HEAD, inventory.armor.get(3).copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, inventory.offhand.get(0).copy());
    }

    private CompoundTag createSnapshot(ServerPlayer player, DamageSource damageSource, long timestamp) {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlayerName", player.getName().getString());
        tag.putUUID("PlayerId", player.getUUID());
        tag.putLong("Timestamp", timestamp);
        tag.putString("Dimension", player.level().dimension().location().toString());
        tag.putDouble("PosX", player.getX());
        tag.putDouble("PosY", player.getY());
        tag.putDouble("PosZ", player.getZ());
        tag.putString("DeathCause", damageSource.typeHolder().value().msgId());

        ListTag items = new ListTag();
        player.getInventory().save(items);
        tag.put("Items", items);
        tag.putInt("SelectedSlot", player.getInventory().selected);
        tag.putInt("XpLevel", player.experienceLevel);
        tag.putFloat("XpP", player.experienceProgress);
        tag.putInt("XpTotal", player.totalExperience);
        return tag;
    }

    private Path getPlayerDir(MinecraftServer server, UUID playerId) {
        Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        return worldRoot.resolve("deathinventorybackup").resolve(playerId.toString());
    }

    private BackupInfo readBackupInfo(Path path) {
        String fileName = path.getFileName().toString();
        String backupId = fileName.substring(0, fileName.length() - 4);
        long timestamp = parseTimestamp(backupId);
        String deathCause = "unknown";
        String dimension = "?";
        int xpLevel = -1;

        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            if (tag.contains("Timestamp", Tag.TAG_LONG)) {
                timestamp = tag.getLong("Timestamp");
            }
            if (tag.contains("DeathCause", Tag.TAG_STRING)) {
                deathCause = tag.getString("DeathCause");
            }
            if (tag.contains("Dimension", Tag.TAG_STRING)) {
                dimension = tag.getString("Dimension");
            }
            if (tag.contains("XpLevel", Tag.TAG_INT)) {
                xpLevel = tag.getInt("XpLevel");
            }
        } catch (IOException ignored) {
        }

        return new BackupInfo(backupId, timestamp, deathCause, dimension, xpLevel);
    }

    private long parseTimestamp(String backupId) {
        try {
            return Long.parseLong(backupId);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void pruneOldBackups(Path playerDir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerDir, "*.dat")) {
            for (Path path : stream) {
                files.add(path);
            }
        }

        if (files.size() <= MAX_BACKUPS_PER_PLAYER) {
            return;
        }

        files.sort(Comparator.comparingLong(path -> parseTimestamp(path.getFileName().toString().replace(".dat", ""))));
        int toDelete = files.size() - MAX_BACKUPS_PER_PLAYER;
        for (int i = 0; i < toDelete; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    public String formatTimestamp(long timestamp) {
        return DISPLAY_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    public record BackupInfo(String id, long timestamp, String deathCause, String dimension, int xpLevel) {
    }

    public record BackupEntry(BackupInfo info, CompoundTag snapshot) {
    }
}

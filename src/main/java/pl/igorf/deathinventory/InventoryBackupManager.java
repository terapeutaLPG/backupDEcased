package pl.igorf.deathinventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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

        CompoundTag snapshot = createSnapshot(player, damageSource);
        Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path playerDir = worldRoot.resolve("deathinventorybackup").resolve(player.getUUID().toString());
        String fileName = Instant.now().toEpochMilli() + ".dat";

        ioExecutor.execute(() -> {
            try {
                Files.createDirectories(playerDir);
                Path target = playerDir.resolve(fileName);
                NbtIo.writeCompressed(snapshot, target.toFile());
                pruneOldBackups(playerDir);
                LOGGER.info("Zapisano ekwipunek gracza {} ({})", player.getName().getString(), fileName);
            } catch (IOException exception) {
                LOGGER.error("Nie udalo sie zapisac ekwipunku gracza {}", player.getName().getString(), exception);
            }
        });
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
            return Optional.of(NbtIo.readCompressed(path.toFile()));
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

        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private CompoundTag createSnapshot(ServerPlayer player, DamageSource damageSource) {
        CompoundTag tag = new CompoundTag();
        tag.putString("PlayerName", player.getName().getString());
        tag.putUUID("PlayerId", player.getUUID());
        tag.putLong("Timestamp", Instant.now().toEpochMilli());
        tag.putString("Dimension", player.level().dimension().location().toString());
        tag.putDouble("PosX", player.getX());
        tag.putDouble("PosY", player.getY());
        tag.putDouble("PosZ", player.getZ());
        tag.putString("DeathCause", damageSource.getMsgId());

        ListTag items = new ListTag();
        player.getInventory().save(items);
        tag.put("Items", items);
        tag.putInt("SelectedSlot", player.getInventory().selected);
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

        try {
            CompoundTag tag = NbtIo.readCompressed(path.toFile());
            if (tag.contains("Timestamp", Tag.TAG_LONG)) {
                timestamp = tag.getLong("Timestamp");
            }
            if (tag.contains("DeathCause", Tag.TAG_STRING)) {
                deathCause = tag.getString("DeathCause");
            }
            if (tag.contains("Dimension", Tag.TAG_STRING)) {
                dimension = tag.getString("Dimension");
            }
        } catch (IOException ignored) {
        }

        return new BackupInfo(backupId, timestamp, deathCause, dimension);
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

    public record BackupInfo(String id, long timestamp, String deathCause, String dimension) {
    }
}

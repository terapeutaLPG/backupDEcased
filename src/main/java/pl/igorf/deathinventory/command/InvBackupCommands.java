package pl.igorf.deathinventory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import pl.igorf.deathinventory.InventoryBackupManager;

import java.util.List;
import java.util.Optional;

public final class InvBackupCommands {
    private static final SimpleCommandExceptionType NO_BACKUPS = new SimpleCommandExceptionType(
            Component.literal("Brak zapisanych ekwipunkow dla tego gracza.")
    );
    private static final SimpleCommandExceptionType BACKUP_NOT_FOUND = new SimpleCommandExceptionType(
            Component.literal("Nie znaleziono backupu o podanym ID.")
    );
    private InvBackupCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, InventoryBackupManager manager) {
        dispatcher.register(
                Commands.literal("invbackup")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .executes(context -> listBackups(context, manager))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .executes(context -> restoreBackup(context, manager, false)))))
                        .then(Commands.literal("restorelatest")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .executes(context -> restoreBackup(context, manager, true))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.string())
                                                .executes(context -> deleteBackup(context, manager)))))
        );
    }

    private static int listBackups(CommandContext<CommandSourceStack> context, InventoryBackupManager manager)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
        List<InventoryBackupManager.BackupInfo> backups = manager.listBackups(
                context.getSource().getServer(),
                target.getUUID()
        );

        if (backups.isEmpty()) {
            throw NO_BACKUPS.create();
        }

        context.getSource().sendSuccess(() -> Component.literal(
                "Backupy gracza " + target.getName().getString() + " (" + backups.size() + "):"
        ), false);

        int index = 1;
        for (InventoryBackupManager.BackupInfo backup : backups) {
            final int lineNumber = index++;
            final String line = lineNumber + ". ID: " + backup.id()
                    + " | " + manager.formatTimestamp(backup.timestamp())
                    + " | " + backup.dimension()
                    + " | smierc: " + backup.deathCause();
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }

        return backups.size();
    }

    private static int restoreBackup(CommandContext<CommandSourceStack> context, InventoryBackupManager manager,
                                     boolean latest) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");

        Optional<InventoryBackupManager.BackupInfo> latestInfo = latest
                ? manager.getLatestBackup(context.getSource().getServer(), target.getUUID())
                : Optional.empty();

        String backupId = latest
                ? latestInfo.map(InventoryBackupManager.BackupInfo::id).orElse(null)
                : StringArgumentType.getString(context, "id");

        if (backupId == null) {
            throw NO_BACKUPS.create();
        }

        Optional<net.minecraft.nbt.CompoundTag> snapshot = manager.loadBackup(
                context.getSource().getServer(),
                target.getUUID(),
                backupId
        );

        if (snapshot.isEmpty()) {
            throw BACKUP_NOT_FOUND.create();
        }

        manager.applySnapshot(target, snapshot.get());

        String playerName = target.getName().getString();
        context.getSource().sendSuccess(
                () -> Component.literal("Przywrocono ekwipunek gracza " + playerName + " z backupu " + backupId + "."),
                true
        );
        target.sendSystemMessage(Component.literal("Operator przywrocil Twoj ekwipunek z backupu."));
        return 1;
    }

    private static int deleteBackup(CommandContext<CommandSourceStack> context, InventoryBackupManager manager)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
        String backupId = StringArgumentType.getString(context, "id");

        boolean deleted = manager.deleteBackup(
                context.getSource().getServer(),
                target.getUUID(),
                backupId
        );

        if (!deleted) {
            throw BACKUP_NOT_FOUND.create();
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Usunieto backup " + backupId + " gracza " + target.getName().getString() + "."),
                true
        );
        return 1;
    }
}

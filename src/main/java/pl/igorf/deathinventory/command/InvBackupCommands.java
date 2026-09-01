package pl.igorf.deathinventory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkHooks;
import pl.igorf.deathinventory.InventoryBackupManager;
import pl.igorf.deathinventory.menu.BackupViewMenu;

import java.util.List;
import java.util.Optional;

public final class InvBackupCommands {
    private static final SimpleCommandExceptionType NO_BACKUPS = new SimpleCommandExceptionType(
            Component.literal("Brak zapisanych ekwipunkow dla tego gracza.")
    );
    private static final SimpleCommandExceptionType BACKUP_NOT_FOUND = new SimpleCommandExceptionType(
            Component.literal("Nie znaleziono backupu o podanym numerze.")
    );
    private static final SimpleCommandExceptionType PLAYER_ONLY = new SimpleCommandExceptionType(
            Component.literal("Ta komenda wymaga gracza (nie dziala z konsoli).")
    );

    private InvBackupCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, InventoryBackupManager manager) {
        SuggestionProvider<CommandSourceStack> indexSuggestions = backupIndexSuggestions(manager);

        dispatcher.register(
                Commands.literal("invbackup")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("list")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .executes(context -> listBackups(context, manager))))
                        .then(Commands.literal("gui")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .then(Commands.argument("nr", IntegerArgumentType.integer(1))
                                                .suggests(indexSuggestions)
                                                .executes(context -> openGui(context, manager)))))
                        .then(Commands.literal("restore")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .then(Commands.argument("nr", IntegerArgumentType.integer(1))
                                                .suggests(indexSuggestions)
                                                .executes(context -> restoreBackup(context, manager)))))
                        .then(Commands.literal("restorelatest")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .executes(context -> restoreLatest(context, manager))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("gracz", EntityArgument.player())
                                        .then(Commands.argument("nr", IntegerArgumentType.integer(1))
                                                .suggests(indexSuggestions)
                                                .executes(context -> deleteBackup(context, manager)))))
        );
    }

    private static SuggestionProvider<CommandSourceStack> backupIndexSuggestions(InventoryBackupManager manager) {
        return (context, builder) -> {
            try {
                ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
                List<InventoryBackupManager.BackupInfo> backups = manager.listBackups(
                        context.getSource().getServer(),
                        target.getUUID()
                );
                for (int i = 0; i < backups.size(); i++) {
                    InventoryBackupManager.BackupInfo info = backups.get(i);
                    int number = i + 1;
                    builder.suggest(number, Component.literal(
                            "#" + number + " | " + manager.formatTimestamp(info.timestamp())
                                    + (info.xpLevel() >= 0 ? " | Lvl " + info.xpLevel() : "")
                                    + " | " + formatDimension(info.dimension())
                                    + " | " + formatDeathCause(info.deathCause())
                    ));
                }
            } catch (CommandSyntaxException ignored) {
            }
            return builder.buildFuture();
        };
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

        String playerName = target.getName().getString();
        context.getSource().sendSuccess(() -> Component.literal("Backupy gracza ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(playerName).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + backups.size() + ")").withStyle(ChatFormatting.GRAY)), false);

        for (int i = 0; i < backups.size(); i++) {
            final int number = i + 1;
            final InventoryBackupManager.BackupInfo backup = backups.get(i);
            context.getSource().sendSuccess(() -> buildBackupInfoLine(number, backup, manager), false);
            context.getSource().sendSuccess(() -> buildBackupActionLine(number, playerName), false);
        }

        context.getSource().sendSuccess(() -> Component.literal(
                "Wskazowka: uzyc /invbackup gui <gracz> <nr> lub kliknij [Podglad]. Numer podpowiada sie przez TAB."
        ).withStyle(ChatFormatting.DARK_GRAY), false);

        return backups.size();
    }

    private static int openGui(CommandContext<CommandSourceStack> context, InventoryBackupManager manager)
            throws CommandSyntaxException {
        if (!(context.getSource().getEntity() instanceof ServerPlayer operator)) {
            throw PLAYER_ONLY.create();
        }

        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
        int number = IntegerArgumentType.getInteger(context, "nr");
        InventoryBackupManager.BackupEntry entry = resolveBackup(context, manager, target, number);

        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Backup #" + number + " - " + target.getName().getString());
            }

            @Override
            public BackupViewMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new BackupViewMenu(
                        containerId,
                        inventory,
                        target.getUUID(),
                        entry.info().id(),
                        target.getName().getString(),
                        number,
                        entry.snapshot(),
                        manager
                );
            }
        };

        NetworkHooks.openScreen(operator, provider, buffer -> {
            buffer.writeUUID(target.getUUID());
            buffer.writeUtf(entry.info().id());
            buffer.writeUtf(target.getName().getString());
            buffer.writeInt(number);
        });

        return 1;
    }

    private static int restoreBackup(CommandContext<CommandSourceStack> context, InventoryBackupManager manager)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
        int number = IntegerArgumentType.getInteger(context, "nr");
        InventoryBackupManager.BackupEntry entry = resolveBackup(context, manager, target, number);

        manager.applySnapshot(target, entry.snapshot());

        context.getSource().sendSuccess(
                () -> Component.literal("Przywrocono ekwipunek i XP gracza ")
                        .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" z backupu #" + number + ".").withStyle(ChatFormatting.GREEN)),
                true
        );
        target.sendSystemMessage(Component.literal("Operator przywrocil Twoj ekwipunek i poziom doswiadczenia z backupu."));
        return 1;
    }

    private static int restoreLatest(CommandContext<CommandSourceStack> context, InventoryBackupManager manager)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
        Optional<InventoryBackupManager.BackupInfo> latest = manager.getLatestBackup(
                context.getSource().getServer(),
                target.getUUID()
        );

        if (latest.isEmpty()) {
            throw NO_BACKUPS.create();
        }

        Optional<net.minecraft.nbt.CompoundTag> snapshot = manager.loadBackup(
                context.getSource().getServer(),
                target.getUUID(),
                latest.get().id()
        );

        if (snapshot.isEmpty()) {
            throw BACKUP_NOT_FOUND.create();
        }

        manager.applySnapshot(target, snapshot.get());

        context.getSource().sendSuccess(
                () -> Component.literal("Przywrocono ostatni ekwipunek i XP gracza ")
                        .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(".").withStyle(ChatFormatting.GREEN)),
                true
        );
        target.sendSystemMessage(Component.literal("Operator przywrocil Twoj ekwipunek i poziom doswiadczenia z backupu."));
        return 1;
    }

    private static int deleteBackup(CommandContext<CommandSourceStack> context, InventoryBackupManager manager)
            throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "gracz");
        int number = IntegerArgumentType.getInteger(context, "nr");
        InventoryBackupManager.BackupEntry entry = resolveBackup(context, manager, target, number);

        boolean deleted = manager.deleteBackup(
                context.getSource().getServer(),
                target.getUUID(),
                entry.info().id()
        );

        if (!deleted) {
            throw BACKUP_NOT_FOUND.create();
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Usunieto backup #" + number + " gracza ")
                        .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(".").withStyle(ChatFormatting.RED)),
                true
        );
        return 1;
    }

    private static InventoryBackupManager.BackupEntry resolveBackup(
            CommandContext<CommandSourceStack> context,
            InventoryBackupManager manager,
            ServerPlayer target,
            int number
    ) throws CommandSyntaxException {
        Optional<InventoryBackupManager.BackupEntry> entry = manager.getBackupByIndex(
                context.getSource().getServer(),
                target.getUUID(),
                number
        );

        if (entry.isEmpty()) {
            throw BACKUP_NOT_FOUND.create();
        }

        return entry.get();
    }

    private static Component buildBackupInfoLine(int number, InventoryBackupManager.BackupInfo backup,
                                                 InventoryBackupManager manager) {
        MutableComponent line = Component.empty()
                .append(Component.literal("#" + number + " ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(manager.formatTimestamp(backup.timestamp())).withStyle(ChatFormatting.WHITE));
        if (backup.xpLevel() >= 0) {
            line = line.append(Component.literal(" | Lvl ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(String.valueOf(backup.xpLevel())).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return line.append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatDimension(backup.dimension())).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(formatDeathCause(backup.deathCause())).withStyle(ChatFormatting.RED));
    }

    private static Component buildBackupActionLine(int number, String playerName) {
        String quotedName = quotePlayerName(playerName);
        return Component.empty()
                .append(actionButton("[Podglad]", ChatFormatting.AQUA,
                        "invbackup gui " + quotedName + " " + number,
                        "Otworz podglad ekwipunku", ClickEvent.Action.RUN_COMMAND))
                .append(Component.literal("  ").withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                .append(actionButton("[Przywroc]", ChatFormatting.GREEN,
                        "invbackup restore " + quotedName + " " + number,
                        "Przywroc ten ekwipunek", ClickEvent.Action.RUN_COMMAND))
                .append(Component.literal("  ").withStyle(Style.EMPTY.withColor(ChatFormatting.WHITE)))
                .append(actionButton("[Usun]", ChatFormatting.RED,
                        "invbackup delete " + quotedName + " " + number,
                        "Usun ten backup", ClickEvent.Action.SUGGEST_COMMAND));
    }

    private static String quotePlayerName(String playerName) {
        if (playerName.indexOf(' ') >= 0) {
            return "\"" + playerName.replace("\"", "\\\"") + "\"";
        }
        return playerName;
    }

    private static MutableComponent actionButton(String label, ChatFormatting color, String command, String hover,
                                                 ClickEvent.Action action) {
        return Component.literal(label).withStyle(Style.EMPTY
                .withColor(color)
                .withClickEvent(new ClickEvent(action, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static String formatDimension(String dimension) {
        if (dimension.endsWith(":overworld")) {
            return "Overworld";
        }
        if (dimension.endsWith(":the_nether")) {
            return "Nether";
        }
        if (dimension.endsWith(":the_end")) {
            return "End";
        }
        int separator = dimension.indexOf(':');
        return separator >= 0 ? dimension.substring(separator + 1) : dimension;
    }

    private static String formatDeathCause(String cause) {
        return switch (cause) {
            case "genericKill" -> "smierc";
            case "player" -> "gracz";
            case "mob" -> "mob";
            case "fall" -> "upadek";
            case "explosion", "explosion.player" -> "wybuch";
            case "fireball" -> "kula ognia";
            case "lava" -> "lawa";
            case "drown" -> "utonięcie";
            case "starve" -> "glod";
            case "wither" -> "wither";
            case "magic" -> "magia";
            case "indirectMagic" -> "magia (posrednia)";
            case "thorns" -> "kolce";
            case "arrow" -> "strzala";
            case "trident" -> "trojzab";
            case "lightningBolt" -> "piorun";
            case "cramming" -> "zgniecenie";
            case "flyIntoWall" -> "kontuzja";
            case "anvil" -> "kowadlo";
            case "fallingBlock" -> "spadajacy blok";
            case "fireworks" -> "fajerwerki";
            default -> cause;
        };
    }
}

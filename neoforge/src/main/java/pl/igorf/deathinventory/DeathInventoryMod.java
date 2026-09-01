package pl.igorf.deathinventory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import pl.igorf.deathinventory.command.InvBackupCommands;
import pl.igorf.deathinventory.handler.DeathEventHandler;

@Mod(DeathInventoryMod.MOD_ID)
public class DeathInventoryMod {
    public static final String MOD_ID = "deathinventorybackup";

    private final InventoryBackupManager backupManager = new InventoryBackupManager();

    public DeathInventoryMod(IEventBus modEventBus) {
        ModMenus.MENUS.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new DeathEventHandler(backupManager));
    }

    public InventoryBackupManager getBackupManager() {
        return backupManager;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        InvBackupCommands.register(event.getDispatcher(), backupManager);
    }
}

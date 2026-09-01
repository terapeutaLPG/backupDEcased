package pl.igorf.deathinventory;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import pl.igorf.deathinventory.command.InvBackupCommands;
import pl.igorf.deathinventory.handler.DeathEventHandler;

@Mod(DeathInventoryMod.MOD_ID)
public class DeathInventoryMod {
    public static final String MOD_ID = "deathinventorybackup";

    private final InventoryBackupManager backupManager = new InventoryBackupManager();

    public DeathInventoryMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DeathEventHandler(backupManager));
    }

    public InventoryBackupManager getBackupManager() {
        return backupManager;
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        InvBackupCommands.register(event.getDispatcher(), backupManager);
    }
}

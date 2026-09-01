package pl.igorf.deathinventory.handler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pl.igorf.deathinventory.InventoryBackupManager;

public class DeathEventHandler {
    private final InventoryBackupManager backupManager;

    public DeathEventHandler(InventoryBackupManager backupManager) {
        this.backupManager = backupManager;
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        backupManager.saveOnDeath(player, event.getSource());
    }
}

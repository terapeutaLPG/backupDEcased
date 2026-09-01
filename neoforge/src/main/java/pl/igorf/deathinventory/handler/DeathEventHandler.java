package pl.igorf.deathinventory.handler;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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

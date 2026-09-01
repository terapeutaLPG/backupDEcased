package pl.igorf.deathinventory.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import pl.igorf.deathinventory.DeathInventoryMod;
import pl.igorf.deathinventory.ModMenus;

@EventBusSubscriber(modid = DeathInventoryMod.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.BACKUP_VIEW.get(), BackupViewScreen::new);
    }
}

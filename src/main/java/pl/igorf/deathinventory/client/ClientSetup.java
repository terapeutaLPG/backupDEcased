package pl.igorf.deathinventory.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import pl.igorf.deathinventory.DeathInventoryMod;
import pl.igorf.deathinventory.ModMenus;

@Mod.EventBusSubscriber(modid = DeathInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.BACKUP_VIEW.get(), BackupViewScreen::new));
    }
}

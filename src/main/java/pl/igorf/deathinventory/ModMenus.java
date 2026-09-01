package pl.igorf.deathinventory;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pl.igorf.deathinventory.menu.BackupViewMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, DeathInventoryMod.MOD_ID);

    public static final RegistryObject<MenuType<BackupViewMenu>> BACKUP_VIEW =
            MENUS.register("backup_view", () -> IForgeMenuType.create(BackupViewMenu::fromNetwork));

    private ModMenus() {
    }
}

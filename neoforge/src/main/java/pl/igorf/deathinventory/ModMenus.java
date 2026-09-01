package pl.igorf.deathinventory;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import pl.igorf.deathinventory.menu.BackupViewMenu;

import java.util.function.Supplier;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, DeathInventoryMod.MOD_ID);

    public static final Supplier<MenuType<BackupViewMenu>> BACKUP_VIEW =
            MENUS.register("backup_view", () -> IMenuTypeExtension.create(BackupViewMenu::fromNetwork));

    private ModMenus() {
    }
}

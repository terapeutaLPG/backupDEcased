package pl.igorf.deathinventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import pl.igorf.deathinventory.menu.BackupViewMenu;

public class BackupViewScreen extends AbstractContainerScreen<BackupViewMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    public BackupViewScreen(BackupViewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 238;
        inventoryLabelY = 112;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Przywroc"), button -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 8, topPos + 96, 54, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        String title = menu.getBackupNumber() > 0
                ? "Backup #" + menu.getBackupNumber() + " - " + menu.getTargetPlayerName()
                : "Backup - " + menu.getTargetPlayerName();
        graphics.drawString(font, title, leftPos + 62, topPos + 6, 0x404040, false);
        renderTooltip(graphics, mouseX, mouseY);
    }
}

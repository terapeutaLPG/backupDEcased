package pl.igorf.deathinventory.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import pl.igorf.deathinventory.DeathInventoryMod;
import pl.igorf.deathinventory.menu.BackupViewMenu;

public class BackupViewScreen extends AbstractContainerScreen<BackupViewMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");

    public BackupViewScreen(BackupViewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("Przywroc ekwipunek"), button -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            }
        }).bounds(leftPos + 52, topPos + 62, 100, 20).build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, "Backup: " + menu.getTargetPlayerName(), leftPos + 8, topPos + 6, 0x404040, false);
        renderTooltip(graphics, mouseX, mouseY);
    }
}

package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;

/** Po otwarciu skrzyni sam przenosi z niej przedmioty do ekwipunku. */
public final class ChestStealerModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("Opóźnienie (ticki)", 0, 10, 2, 1));
	private int ticks;

	public ChestStealerModule() {
		super("cheststealer", "Chest Stealer", "Automatycznie zabiera zawartość otwartej skrzyni", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return;
		if (!(mc.player.containerMenu instanceof ChestMenu menu)) {
			ticks = 0;
			return;
		}
		if (++ticks <= delay.getInt()) return;
		ticks = 0;
		int size = menu.getRowCount() * 9;
		for (int i = 0; i < size; i++) {
			if (!menu.getSlot(i).getItem().isEmpty()) {
				dev.nebula.client.Compat.quickMove(mc, menu.containerId, i);
				return;
			}
		}
	}
}

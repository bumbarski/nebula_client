package dev.nebula.client.module.impl;

import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;

import java.util.Random;

/** ChestStealer — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur): opoznienie w ms, losowosc, zamykanie po oproznieniu. */
public final class ChestStealerModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("Opóźnienie (ms)", 0, 1000, 100, 10));
	private final BoolSetting random = add(new BoolSetting("Losowe opóźnienie", false));
	private final BoolSetting close = add(new BoolSetting("Zamknij po oproznieniu", false));
	private final Random rnd = new Random();
	private long last;
	private long wait;

	public ChestStealerModule() {
		super("cheststealer", "Chest Stealer", "Automatycznie zabiera zawartość otwartej skrzyni", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null) return;
		if (!(mc.player.containerMenu instanceof ChestMenu menu)) return;
		int size = menu.getRowCount() * 9;
		long now = System.currentTimeMillis();
		if (now - last >= wait) {
			for (int i = 0; i < size; i++) {
				if (!menu.getSlot(i).getItem().isEmpty()) {
					Reflect.click(mc, menu.containerId, i, 0, "QUICK_MOVE");
					last = now;
					wait = (long) (delay.get() + (random.value && delay.get() != 0 ? rnd.nextInt((int) delay.get()) : 0));
					break;
				}
			}
		}
		if (close.value) {
			for (int i = 0; i < size; i++) if (!menu.getSlot(i).getItem().isEmpty()) return;
			mc.player.closeContainer();
		}
	}
}

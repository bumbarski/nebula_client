package dev.nebula.client.module.impl;

import dev.nebula.client.Reflect;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;

import java.util.Random;

/** ChestStealer — logika przeniesiona z ThunderHack Recode (GPL-3.0, pan4ur): jeden przedmiot na opoznienie (ms), losowosc, zamykanie. */
public final class ChestStealerModule extends Module {
	private final NumberSetting delay = add(new NumberSetting("Opóźnienie (ms)", 0, 1000, 100, 10));
	private final BoolSetting random = add(new BoolSetting("Losowe opóźnienie", false));
	private final BoolSetting close = add(new BoolSetting("Zamknij gdy pusta", false));
	private final Random rnd = new Random();
	private long last;

	public ChestStealerModule() {
		super("cheststealer", "ChestStealer", "Zabiera przedmioty z otwartej skrzyni", Category.GAME, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null || mc.gameMode == null || !(mc.player.containerMenu instanceof ChestMenu chest)) return;
		int size = chest.getRowCount() * 9;
		int d = delay.getInt();
		long now = System.currentTimeMillis();
		boolean empty = true;
		for (int i = 0; i < size; i++) {
			if (!chest.getSlot(i).hasItem()) continue;
			empty = false;
			long need = d + (random.value && d != 0 ? rnd.nextInt(d) : 0);
			if (now - last >= need) {
				Reflect.click(mc, chest.containerId, i, 0, "QUICK_MOVE");
				last = now;
				return;
			}
		}
		if (empty && close.value) mc.player.closeContainer();
	}
}

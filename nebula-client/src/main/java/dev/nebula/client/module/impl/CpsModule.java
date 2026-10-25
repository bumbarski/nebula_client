package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class CpsModule extends HudModule {
	private final BoolSetting showRight = add(new BoolSetting("Pokaż PPM", true));

	private final Deque<Long> left = new ArrayDeque<>();
	private final Deque<Long> right = new ArrayDeque<>();
	private boolean lastLeft;
	private boolean lastRight;

	public CpsModule() {
		super("cps", "CPS", "Kliknięcia na sekundę (LPM / PPM)", Category.HUD, true, 4, 44);
	}

	/** Liczone co klatke, wiec dokladniejsze niz co tick. */
	@Override
	public void onFrame(Minecraft mc) {
		long now = System.currentTimeMillis();
		boolean l = mc.options.keyAttack.isDown();
		boolean r = mc.options.keyUse.isDown();
		if (l && !lastLeft) left.addLast(now);
		if (r && !lastRight) right.addLast(now);
		lastLeft = l;
		lastRight = r;
		prune(left, now);
		prune(right, now);
	}

	private static void prune(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) {
			clicks.removeFirst();
		}
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		String value = showRight.value ? left.size() + " | " + right.size() : String.valueOf(left.size());
		drawRows(g, List.<String[]>of(new String[]{"CPS", value}));
	}
}

package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.HudModule;
import dev.nebula.client.setting.BoolSetting;
import dev.nebula.client.ui.Gfx;
import dev.nebula.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class KeystrokesModule extends HudModule {
	private static final int KEY = 20;
	private static final int GAP = 2;

	private final BoolSetting mouse = add(new BoolSetting("Przyciski myszy", true));
	private final BoolSetting space = add(new BoolSetting("Spacja", true));

	// W, A, S, D, spacja, LPM, PPM
	private final float[] anim = new float[7];
	private long lastNanos = System.nanoTime();

	public KeystrokesModule() {
		super("keystrokes", "Klawisze", "WASD, spacja i przyciski myszy", Category.HUD, true, 4, 136);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;

		Options o = mc.options;
		boolean[] down = new boolean[7];
		if (!preview) {
			down[0] = o.keyUp.isDown();
			down[1] = o.keyLeft.isDown();
			down[2] = o.keyDown.isDown();
			down[3] = o.keyRight.isDown();
			down[4] = o.keyJump.isDown();
			down[5] = o.keyAttack.isDown();
			down[6] = o.keyUse.isDown();
		}

		int step = KEY + GAP;
		int totalW = KEY * 3 + GAP * 2;

		key(g, 0, down[0], dt, x + step, y, KEY, KEY, "W");
		key(g, 1, down[1], dt, x, y + step, KEY, KEY, "A");
		key(g, 2, down[2], dt, x + step, y + step, KEY, KEY, "S");
		key(g, 3, down[3], dt, x + step * 2, y + step, KEY, KEY, "D");

		int bottom = y + step * 2 - GAP;
		if (mouse.value) {
			int row = y + step * 2;
			int half = (totalW - GAP) / 2;
			key(g, 5, down[5], dt, x, row, half, 16, "LPM");
			key(g, 6, down[6], dt, x + half + GAP, row, totalW - half - GAP, 16, "PPM");
			bottom = row + 16;
		}
		if (space.value) {
			int row = bottom + GAP;
			key(g, 4, down[4], dt, x, row, totalW, 12, "");
			// pasek symbolizujacy spacje
			int barW = totalW / 2;
			int barColor = Gfx.lerp(0xFFFFFFFF, 0xFF0B0B12, anim[4]);
			Gfx.roundRect(g, x + (totalW - barW) / 2, row + 5, barW, 2, 1, barColor);
			bottom = row + 12;
		}

		w = totalW;
		h = bottom - y;
	}

	private void key(GuiGraphicsExtractor g, int idx, boolean down, float dt,
					 int kx, int ky, int kw, int kh, String label) {
		if (Theme.animations()) {
			anim[idx] += ((down ? 1f : 0f) - anim[idx]) * Math.min(1f, dt * 18f);
		} else {
			anim[idx] = down ? 1f : 0f;
		}
		float t = anim[idx];

		int idle = Gfx.alpha(Theme.BG, Theme.opacity());
		int active = Gfx.alpha(Theme.accent(), 0.9f);
		Gfx.roundRect(g, kx, ky, kw, kh, 3, Gfx.lerp(idle, active, t));

		if (!label.isEmpty()) {
			int fg = Gfx.lerp(0xFFFFFFFF, 0xFF0B0B12, t);
			Gfx.text(g, label, kx + (kw - Gfx.width(label)) / 2, ky + (kh - Gfx.fontHeight()) / 2 + 1, fg, false);
		}
	}
}

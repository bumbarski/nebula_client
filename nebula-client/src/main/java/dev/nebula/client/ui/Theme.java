package dev.nebula.client.ui;

import dev.nebula.client.module.impl.AppearanceModule;

public final class Theme {
	private Theme() {
	}

	public static final String[] NAMES = {"Fiolet", "Cyjan", "Róż", "Zieleń", "Pomarańcz"};
	private static final int[] ACCENT = {0xFF8B5CF6, 0xFF22D3EE, 0xFFF472B6, 0xFF4ADE80, 0xFFFB923C};
	private static final int[] ACCENT_DARK = {0xFF5B34C9, 0xFF0E8FA8, 0xFFC0407F, 0xFF22A055, 0xFFC4601A};

	public static final int BG = 0xFF12121A;
	public static final int PANEL = 0xFF191924;
	public static final int HEADER = 0xFF0E0E16;
	public static final int ROW_HOVER = 0xFF262637;
	public static final int TRACK = 0xFF2E2E42;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int MUTED = 0xFFA8A8C0;

	private static AppearanceModule appearance;

	public static void bind(AppearanceModule module) {
		appearance = module;
	}

	public static int accent() {
		return appearance == null ? ACCENT[0] : ACCENT[appearance.theme.index()];
	}

	public static int accentDark() {
		return appearance == null ? ACCENT_DARK[0] : ACCENT_DARK[appearance.theme.index()];
	}

	public static float opacity() {
		return appearance == null ? 0.85f : appearance.opacity.getFloat();
	}

	public static boolean animations() {
		return appearance == null || appearance.animations.value;
	}
}

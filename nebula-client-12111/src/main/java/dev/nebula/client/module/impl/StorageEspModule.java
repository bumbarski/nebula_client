package dev.nebula.client.module.impl;

import dev.nebula.client.setting.BoolSetting;

/** Storage ESP: skrzynie, beczki, shulkery i inne pojemniki przez sciany, z tracerami. */
public final class StorageEspModule extends EspModule {
	private final BoolSetting chests = add(new BoolSetting("Skrzynie", true));
	private final BoolSetting trapped = add(new BoolSetting("Skrzynie pułapki", true));
	private final BoolSetting ender = add(new BoolSetting("Ender chest", true));
	private final BoolSetting barrels = add(new BoolSetting("Beczki", true));
	private final BoolSetting shulkers = add(new BoolSetting("Shulkery", true));
	private final BoolSetting other = add(new BoolSetting("Lejki, piece, dispensery", false));

	public StorageEspModule() {
		super("storageesp", "Storage ESP", "Pojemniki (skrzynie, beczki, shulkery) widoczne przez ściany");
	}

	@Override
	protected String settingsKey() {
		return "" + chests.value + trapped.value + ender.value + barrels.value + shulkers.value + other.value;
	}

	@Override
	protected int classify(String p) {
		if (p.equals("chest")) return chests.value ? 0xFFF5A623 : 0;
		if (p.equals("trapped_chest")) return trapped.value ? 0xFFFF4B4B : 0;
		if (p.equals("ender_chest")) return ender.value ? 0xFFA855F7 : 0;
		if (p.equals("barrel")) return barrels.value ? 0xFFB5733B : 0;
		if (p.equals("shulker_box") || p.endsWith("_shulker_box")) return shulkers.value ? 0xFFFF7ACB : 0;
		if (other.value && (p.equals("hopper") || p.equals("dropper") || p.equals("dispenser") || p.equals("furnace")
				|| p.equals("blast_furnace") || p.equals("smoker") || p.equals("brewing_stand") || p.equals("crafter"))) {
			return 0xFF9CA3AF;
		}
		return 0;
	}
}

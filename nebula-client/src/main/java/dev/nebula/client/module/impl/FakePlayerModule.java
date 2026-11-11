package dev.nebula.client.module.impl;

import com.mojang.authlib.GameProfile;
import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** FakePlayer: stawia obok ciebie atrapę gracza (tylko po twojej stronie) do testowania Aury i innych modułów. */
public final class FakePlayerModule extends Module {
	private Player fake;
	private static int nextId = -4242;

	public FakePlayerModule() {
		super("fakeplayer", "Fake Player", "Atrapa gracza do testów (widoczna tylko u ciebie)", Category.GAME, false);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		try {
			Class<?> c = Class.forName("net.minecraft.client.player.RemotePlayer");
			GameProfile gp = new GameProfile(UUID.randomUUID(), "Nebula_Bot");
			Object o = null;
			for (java.lang.reflect.Constructor<?> k : c.getConstructors()) {
				if (k.getParameterCount() == 2 && k.getParameterTypes()[0].isInstance(mc.level)) {
					o = k.newInstance(mc.level, gp);
					break;
				}
			}
			if (o == null) throw new IllegalStateException("brak konstruktora RemotePlayer");
			fake = (Player) o;
			fake.copyPosition(mc.player);
			fake.setYHeadRot(mc.player.getYHeadRot());
			fake.setHealth(20f);
			for (int i = 0; i < 36; i++) fake.getInventory().setItem(i, mc.player.getInventory().getItem(i).copy());
			for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
				try {
					fake.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
				} catch (Throwable ignored) {
				}
			}
			fake.setId(nextId--);
			boolean added = false;
			for (java.lang.reflect.Method m : mc.level.getClass().getMethods()) {
				if (m.getName().equals("addEntity") && m.getParameterCount() == 1) {
					m.invoke(mc.level, fake);
					added = true;
					break;
				}
			}
			if (!added) {
				for (java.lang.reflect.Method m : mc.level.getClass().getMethods()) {
					if (m.getName().equals("addEntity") && m.getParameterCount() == 2) {
						m.invoke(mc.level, fake.getId(), fake);
						break;
					}
				}
			}
		} catch (Throwable t) {
			System.err.println("[Nebula] FakePlayer: " + t);
			fake = null;
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (fake != null && mc.level != null) {
			try {
				for (java.lang.reflect.Method m : mc.level.getClass().getMethods()) {
					if (m.getName().equals("removeEntity") && m.getParameterCount() == 2) {
						m.invoke(mc.level, fake.getId(), Entity.RemovalReason.DISCARDED);
						break;
					}
				}
			} catch (Throwable ignored) {
			}
		}
		fake = null;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null) setEnabled(false);
	}
}

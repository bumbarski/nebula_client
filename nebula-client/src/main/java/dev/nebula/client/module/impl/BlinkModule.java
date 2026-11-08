package dev.nebula.client.module.impl;

import dev.nebula.client.module.Category;
import dev.nebula.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.ArrayList;
import java.util.List;

/** Blink: ruch jest wstrzymywany i wysylany serwerowi dopiero po wylaczeniu (dla innych "teleportujesz sie"). */
public final class BlinkModule extends Module {
	private static boolean holding;
	private static boolean releasing;
	private static final List<Packet<?>> queue = new ArrayList<>();

	public BlinkModule() {
		super("blink", "Blink", "Wstrzymuje pakiety ruchu do wyłączenia modułu", Category.MOVEMENT, false);
	}

	public static boolean intercept(Packet<?> p) {
		if (!holding || releasing || !(p instanceof ServerboundMovePlayerPacket)) return false;
		queue.add(p);
		return true;
	}

	@Override
	protected void onEnable() {
		queue.clear();
		holding = true;
	}

	@Override
	protected void onDisable() {
		holding = false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) {
			releasing = true;
			for (Packet<?> p : queue) mc.getConnection().send(p);
			releasing = false;
		}
		queue.clear();
	}
}

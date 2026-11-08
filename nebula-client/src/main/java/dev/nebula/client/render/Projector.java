package dev.nebula.client.render;

import dev.nebula.client.Compat;
import dev.nebula.client.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

/**
 * Rzutuje punkty swiata na ekran (GUI) wedlug biezacej kamery. Dzieki temu linie i ramki
 * rysujemy zwyklym API interfejsu, bez wlasnych shaderow, i sa widoczne przez sciany.
 */
public final class Projector {
	public final double cx, cy, cz;
	public final int sw, sh;
	private final double fx, fy, fz, rx, rz, ux, uy, uz;
	private final double tanHalf, aspect;

	private Projector(double cx, double cy, double cz, float yaw, float pitch, double fovDeg, int sw, int sh) {
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		this.sw = sw;
		this.sh = sh;
		double psi = Math.toRadians(yaw);
		double th = Math.toRadians(pitch);
		double sp = Math.sin(psi), cp = Math.cos(psi), st = Math.sin(th), ct = Math.cos(th);
		fx = -sp * ct;
		fy = -st;
		fz = cp * ct;
		rx = -cp;
		rz = -sp;
		ux = -sp * st;
		uy = ct;
		uz = cp * st;
		tanHalf = Math.tan(Math.toRadians(fovDeg) / 2.0);
		aspect = (double) sw / Math.max(1, sh);
	}

	public static Projector create(Minecraft mc) {
		Vec3 pos = Compat.cameraPos(mc);
		return new Projector(pos.x, pos.y, pos.z, Compat.cameraYaw(mc), Compat.cameraPitch(mc),
				mc.options.fov().get(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
	}

	/** Glebokosc punktu wzdluz kierunku patrzenia (w blokach). */
	public double depth(double x, double y, double z) {
		return (x - cx) * fx + (y - cy) * fy + (z - cz) * fz;
	}

	public double tanHalf() {
		return tanHalf;
	}

	/** Kierunek patrzenia kamery (yaw w stopniach, jak w Minecrafcie). */
	public float yawDeg() {
		return (float) Math.toDegrees(Math.atan2(-fx, fz));
	}

	/** Pochylenie kamery w stopniach (jak w Minecrafcie: dodatnie = patrzy w dol). */
	public float pitchDeg() {
		return (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -fy))));
	}

	/** Zapisuje wspolrzedne ekranu do out[0], out[1]; false gdy punkt jest za kamera. */
	public boolean toScreen(double x, double y, double z, double[] out) {
		double dx = x - cx, dy = y - cy, dz = z - cz;
		double zc = dx * fx + dy * fy + dz * fz;
		if (zc < 0.05) return false;
		double xc = dx * rx + dz * rz;
		double yc = dx * ux + dy * uy + dz * uz;
		out[0] = sw / 2.0 + xc / (zc * tanHalf * aspect) * (sw / 2.0);
		out[1] = sh / 2.0 - yc / (zc * tanHalf) * (sh / 2.0);
		return true;
	}

	/** Linia miedzy dwoma punktami swiata, przycieta do plaszczyzny bliskiej i do ekranu. */
	public void line(GuiGraphicsExtractor g, double x1, double y1, double z1, double x2, double y2, double z2, int color) {
		double ax = x1 - cx, ay = y1 - cy, az = z1 - cz;
		double bx = x2 - cx, by = y2 - cy, bz = z2 - cz;
		double za = ax * fx + ay * fy + az * fz;
		double zb = bx * fx + by * fy + bz * fz;
		final double near = 0.05;
		if (za < near && zb < near) return;
		if (za < near) {
			double t = (near - za) / (zb - za);
			ax += (bx - ax) * t; ay += (by - ay) * t; az += (bz - az) * t;
			za = near;
		} else if (zb < near) {
			double t = (near - zb) / (za - zb);
			bx += (ax - bx) * t; by += (ay - by) * t; bz += (az - bz) * t;
			zb = near;
		}
		double xa = ax * rx + az * rz, ya = ax * ux + ay * uy + az * uz;
		double xb = bx * rx + bz * rz, yb = bx * ux + by * uy + bz * uz;
		double sx1 = sw / 2.0 + xa / (za * tanHalf * aspect) * (sw / 2.0);
		double sy1 = sh / 2.0 - ya / (za * tanHalf) * (sh / 2.0);
		double sx2 = sw / 2.0 + xb / (zb * tanHalf * aspect) * (sw / 2.0);
		double sy2 = sh / 2.0 - yb / (zb * tanHalf) * (sh / 2.0);
		screenLine(g, sx1, sy1, sx2, sy2, color);
	}

	/** Linia ekranowa przycieta do prostokata ekranu (Liang-Barsky). */
	public void screenLine(GuiGraphicsExtractor g, double x1, double y1, double x2, double y2, int color) {
		double dx = x2 - x1, dy = y2 - y1;
		double t0 = 0, t1 = 1;
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {x1, sw - x1, y1, sh - y1};
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0) {
				if (q[i] < 0) return;
			} else {
				double t = q[i] / p[i];
				if (p[i] < 0) {
					if (t > t1) return;
					if (t > t0) t0 = t;
				} else {
					if (t < t0) return;
					if (t < t1) t1 = t;
				}
			}
		}
		Gfx.line(g, (float) (x1 + t0 * dx), (float) (y1 + t0 * dy), (float) (x1 + t1 * dx), (float) (y1 + t1 * dy), color);
	}

	/** Ramka (12 krawedzi) szescianu/prostopadloscianu. */
	public void box(GuiGraphicsExtractor g, double x0, double y0, double z0, double x1, double y1, double z1, int color) {
		line(g, x0, y0, z0, x1, y0, z0, color); line(g, x1, y0, z0, x1, y0, z1, color);
		line(g, x1, y0, z1, x0, y0, z1, color); line(g, x0, y0, z1, x0, y0, z0, color);
		line(g, x0, y1, z0, x1, y1, z0, color); line(g, x1, y1, z0, x1, y1, z1, color);
		line(g, x1, y1, z1, x0, y1, z1, color); line(g, x0, y1, z1, x0, y1, z0, color);
		line(g, x0, y0, z0, x0, y1, z0, color); line(g, x1, y0, z0, x1, y1, z0, color);
		line(g, x1, y0, z1, x1, y1, z1, color); line(g, x0, y0, z1, x0, y1, z1, color);
	}

	/** Tracer: linia ze srodka ekranu do punktu w swiecie (rysowana tylko gdy punkt jest przed kamera). */
	public void tracer(GuiGraphicsExtractor g, double x, double y, double z, int color) {
		double[] s = new double[2];
		if (!toScreen(x, y, z, s)) return;
		screenLine(g, sw / 2.0, sh / 2.0, s[0], s[1], color);
	}
}

package dev.nebula.client.module;

public enum Category {
	HUD("HUD"),
	VISUAL("Wizualne"),
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	GAME("Gra"),
	SPOTIFY("Spotify");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}

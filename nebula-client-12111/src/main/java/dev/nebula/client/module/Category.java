package dev.nebula.client.module;

public enum Category {
	HUD("HUD"),
	VISUAL("Wizualne"),
	GAME("Gra");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}

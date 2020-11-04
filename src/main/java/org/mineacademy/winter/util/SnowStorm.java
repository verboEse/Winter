package org.mineacademy.winter.util;

import org.bukkit.World;
import org.mineacademy.fo.collection.StrictList;

public class SnowStorm {

	private static final StrictList<String> worlds = new StrictList<>();

	public static final void add(World world) {
		if (!has(world))
			worlds.add(world.getName());
	}

	public static final void remove(World world) {
		worlds.removeWeak(world.getName());
	}

	public static final boolean has(World world) {
		return worlds.contains(world.getName());
	}

	public static final void cleanAll() {
		worlds.clear();
	}
}

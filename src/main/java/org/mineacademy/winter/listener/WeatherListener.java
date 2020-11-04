package org.mineacademy.winter.listener;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.mineacademy.fo.RandomUtil;
import org.mineacademy.winter.settings.Settings;
import org.mineacademy.winter.util.SnowStorm;

public class WeatherListener implements Listener {

	@EventHandler
	public void onWeather(WeatherChangeEvent e) {
		final World world = e.getWorld();

		if (!Settings.ALLOWED_WORLDS.contains(world.getName()))
			return;

		if (Settings.Weather.DISABLE) {
			e.setCancelled(true);

			world.setThunderDuration(0);
			world.setWeatherDuration(0);

			return;
		}

		if (Settings.Weather.SNOW_STORM)
			if (e.toWeatherState()) {
				SnowStorm.add(world);

				if (RandomUtil.chance(Settings.Weather.THUNDER_CHANCE))
					world.setThundering(true);

			} else
				SnowStorm.remove(world);
	}
}

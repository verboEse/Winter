package org.mineacademy.winter.listener;

import static org.mineacademy.winter.settings.Localization.Chest.BREAK_ADMIN;
import static org.mineacademy.winter.settings.Localization.Chest.BREAK_ADMIN_SIGN;
import static org.mineacademy.winter.settings.Localization.Chest.BREAK_OWN;
import static org.mineacademy.winter.settings.Localization.Chest.BREAK_OWN_SIGN;
import static org.mineacademy.winter.settings.Localization.Chest.EXPAND_OWN;
import static org.mineacademy.winter.settings.Localization.Chest.ILLEGAL_ACCESS;
import static org.mineacademy.winter.settings.Localization.Chest.ILLEGAL_BREAK;
import static org.mineacademy.winter.settings.Localization.Chest.ILLEGAL_BREAK_SIGN;
import static org.mineacademy.winter.settings.Localization.Chest.ILLEGAL_EXPAND;
import static org.mineacademy.winter.settings.Localization.Chest.ILLEGAL_INVENTORY_CLICK;
import static org.mineacademy.winter.settings.Localization.Chest.ILLEGAL_PLACE;
import static org.mineacademy.winter.settings.Localization.Chest.OPEN_ADMIN;
import static org.mineacademy.winter.settings.Localization.Chest.OPEN_OWN;
import static org.mineacademy.winter.settings.Localization.Chest.OPEN_PRIVATE;
import static org.mineacademy.winter.settings.Localization.Chest.OPEN_PUBLIC;

import java.util.ArrayList;
import java.util.Arrays;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.PlayerUtil;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.remain.CompMaterial;
import org.mineacademy.fo.remain.CompSound;
import org.mineacademy.winter.model.chest.ChestMatcher;
import org.mineacademy.winter.model.chest.DatedChest;
import org.mineacademy.winter.model.chest.GiftChest;
import org.mineacademy.winter.model.chest.InfiniteChest;
import org.mineacademy.winter.model.chest.TimedChest;
import org.mineacademy.winter.model.chest.WinterChest;
import org.mineacademy.winter.model.data.ChestData;
import org.mineacademy.winter.model.data.PlayerData;
import org.mineacademy.winter.settings.Localization;
import org.mineacademy.winter.settings.Settings;
import org.mineacademy.winter.util.GiftSignUtil;
import org.mineacademy.winter.util.Permissions;
import org.mineacademy.winter.util.WinterUtil;

public class ChestListener implements Listener {

	private final StrictMap<String, ChestMatcher> validChests = new StrictMap<>();

	public ChestListener() {

		validChests.put("[gift]", new GiftChest(null, null, false, null));
		validChests.put("[dated]", DatedChest.MATCHER);
		validChests.put("[timed]", TimedChest.MATCHER);
	}

	/**
	 * REGISTERING OF SIGNS
	 */
	@EventHandler(ignoreCancelled = true)
	public void onSignPlace(SignChangeEvent e) {
		final Block block = e.getBlock();
		final Sign sign = (Sign) e.getBlock().getState();

		final Player pl = e.getPlayer();
		final String[] lines = e.getLines();

		final String title = Common.stripColors(e.getLine(0).toLowerCase());
		final ChestMatcher matcher = validChests.get(title);

		if (matcher == null)
			return;

		if (matcher instanceof GiftChest && !Settings.GiftChest.ENABLED)
			return;

		// No associated chest
		if (GiftSignUtil.findChest(sign) == null) {
			Common.tell(pl, Localization.Chest.NO_CHEST.replace("{sign}", e.getLine(0)));

			destroyBlockSurvival(pl, block);
			return;
		}

		// No permission
		if (!WinterUtil.checkPerm(pl, matcher.getPermission())) {
			destroyBlockSurvival(pl, block);
			return;
		}

		// Invalid format
		if (!matcher.isValidFormat(lines)) {
			Common.tell(pl, Localization.Chest.INVALID_FORMAT.replace("{format}", matcher.getValidFormatExample()));

			destroyBlockSurvival(pl, block);
			return;
		}

		String[] format = Arrays.copyOf(lines, lines.length);

		if (matcher instanceof GiftChest) {
			// Generic
			if (lines[1].isEmpty() && lines[2].isEmpty() && lines[3].isEmpty()) {
				if (!Settings.GiftChest.Public.ALLOW) {
					Common.tell(pl, Localization.Chest.NO_PLAYER);

					destroyBlockSurvival(pl, block);
					return;
				}

				format = Settings.GiftChest.Public.getFormat();

			} else {
				final StrictList<String> receivers = getReceivers(lines);
				format = Settings.GiftChest.Private.getFormat();

				for (int i = 0; i < format.length; i++)
					format[i] = format[i]
							.replace("{receiver_1}", receivers.getOrDefault(0, ""))
							.replace("{receiver_2}", receivers.getOrDefault(1, ""))
							.replace("{receiver_3}", receivers.getOrDefault(2, ""));
			}

			final ArrayList<String> copy = new ArrayList<>();
			copy.add(Settings.GiftChest.TITLE);
			copy.addAll(Arrays.asList(format));

			format = copy.toArray(new String[copy.size()]);
		}

		Common.tell(pl, Localization.Chest.CREATE_SUCCESS);
		GiftSignUtil.registerSign(pl, sign, format);
	}

	private StrictList<String> getReceivers(String[] lines) {
		final StrictList<String> receivers = new StrictList<>();

		for (int i = 1; i < lines.length; i++) {
			final String line = lines[i];

			if (line != null && !line.isEmpty())
				receivers.add(line);
		}

		return receivers;
	}

	private void destroyBlockSurvival(final Player player, final Block block) {
		if (player.getGameMode() != GameMode.CREATIVE)
			block.breakNaturally();
		else
			block.setType(Material.AIR);
	}

	/**
	 * OPENING WINTER CHESTS
	 */
	@EventHandler(ignoreCancelled = true)
	public void onChestOpen(PlayerInteractEvent e) {
		final Block b = e.getClickedBlock();

		if (e.getAction() != Action.RIGHT_CLICK_BLOCK | !(b.getState() instanceof Chest))
			return;

		final WinterChest chest = GiftSignUtil.findChestFull((Chest) b.getState());

		if (chest == null)
			return;

		final Player pl = e.getPlayer();

		try {
			if (chest.isOwner(pl)) {
				Common.tell(pl, OPEN_OWN);

				return;
			}
		} catch (final Throwable t) {
			return;
		}

		if (pl.isSneaking()) {
			Common.tell(pl, ILLEGAL_PLACE);

			e.setCancelled(true);
			return;
		}

		if (chest instanceof GiftChest) {
			final GiftChest gift = (GiftChest) chest;

			if (gift.isPublicChest())
				Common.tell(pl, OPEN_PUBLIC);
			else if (!gift.canAccess(pl)) {
				if (PlayerUtil.hasPerm(pl, Permissions.Chest.ADMIN))
					Common.tell(pl, OPEN_ADMIN);
				else {
					Common.tell(pl, ILLEGAL_ACCESS);

					e.setCancelled(true);
				}
			} else
				Common.tell(pl, OPEN_PRIVATE);
		} else if (chest instanceof InfiniteChest) {
			final InfiniteChest inf = (InfiniteChest) chest;

			inf.onTryOpen(pl);
			e.setCancelled(true);
		}
	}

	/**
	 * DESTROYING SIGNS AND/OR CHESTS
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		final Block b = e.getBlock();
		final BlockState state = b.getState();

		if (state instanceof Chest) {
			final WinterChest chest = GiftSignUtil.findChestFull((Chest) state);

			if (chest == null)
				return;

			final Player pl = e.getPlayer();

			if (chest.isOwner(pl))
				Common.tell(pl, BREAK_OWN);

			else if (PlayerUtil.hasPerm(pl, Permissions.Chest.ADMIN))
				Common.tell(pl, BREAK_ADMIN);

			else {
				Common.tell(pl, ILLEGAL_BREAK);

				e.setCancelled(true);
				return;
			}

			{
				final WinterChest chestStrict = GiftSignUtil.findChestStrict((Chest) state);

				if (chestStrict != null)
					ChestData.$().removeSign(chestStrict.getSign());
			}
		}

		if (CompMaterial.isWallSign(b.getType())) {
			final WinterChest chest = GiftSignUtil.constructChest((Sign) state);

			if (chest == null)
				return;

			final Player pl = e.getPlayer();

			if (chest.isOwner(pl))
				Common.tell(pl, BREAK_OWN_SIGN);

			else if (PlayerUtil.hasPerm(pl, Permissions.Chest.ADMIN))
				Common.tell(pl, BREAK_ADMIN_SIGN);

			else {
				Common.tell(pl, ILLEGAL_BREAK_SIGN);

				e.setCancelled(true);
				return;
			}

			ChestData.$().removeSign(chest.getSign());
			PlayerData.$().onBreak(chest.getChest());
		}
	}

	/**
	 * PLACING CHESTS NEXT TO WINTER CHESTS
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent e) {
		final BlockState state = e.getBlock().getState();

		if (!(state instanceof Chest))
			return;

		final WinterChest chest = GiftSignUtil.findChestFull((Chest) state);

		if (chest == null)
			return;

		final Player pl = e.getPlayer();

		if (chest.isOwner(pl) || PlayerUtil.hasPerm(pl, Permissions.Chest.ADMIN))
			Common.tell(pl, EXPAND_OWN);

		else {
			Common.tell(pl, ILLEGAL_EXPAND);
			e.setCancelled(true);
		}
	}

	/**
	 * CLICKING IN PREVIEW MENU
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInvClick(InventoryClickEvent e) {
		if (e.getInventory() == null)
			return;

		final Player pl = (Player) e.getWhoClicked();

		if (Common.stripColors(e.getView().getTitle()).equals(Common.stripColors(InfiniteChest.TITLE_PREVIEW))) {
			Common.tell(pl, ILLEGAL_INVENTORY_CLICK);
			CompSound.NOTE_BASS.play(pl, 1, (float) Math.random());

			e.setCancelled(true);
		}
	}
}

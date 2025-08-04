package org.aryaisme.fancyshop;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class FancyShop extends JavaPlugin implements Listener, CommandExecutor {
    private Economy economy;
    private PlayerPointsAPI playerPointsAPI;
    private Map<String, Map<String, TradeData>> npcTrades = new HashMap<>();
    private Map<UUID, String> editingNpc = new HashMap<>();
    private Map<UUID, String> editingTradeId = new HashMap<>();
    private Map<UUID, String> chatInput = new HashMap<>();
    private Map<UUID, String> currentTradeNpc = new HashMap<>();
    private Map<UUID, String> currentTradeId = new HashMap<>();
    private Map<String, Location> npcLocations = new HashMap<>();
    private Map<UUID, Integer> playerCurrentPage = new HashMap<>();
    private boolean isLegacyVersion;
    private File tradesFile;
    private Map<UUID, Integer> playerShopPage = new HashMap<>();
    private Map<UUID, Map<Integer, String>> playerTradeSlots = new HashMap<>();
    private final int TRADES_PER_PAGE = 5;
    private File guiConfigFile;
    private File configFile;
    private FileConfiguration tradesConfig;
    private FileConfiguration guiConfig;
    private FileConfiguration config;

    private static final String GOLD_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTQ2N2E3YjlkNzZiYTZkMGZlZDc0MzYwMjUzM2ZjOThjODdhZjBjNjBmODBmMzhkYTc3NGY3YTAxYTIwOTNmYSJ9fX0=";
    private static final String POINT_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWJkYTVmMzE5MzdiMmZmNzU1MjcxZDk3ZjAxYmU4NGQ1MmE0MDdiMzZjYTc3NDUxODU2MTYyYWM2Y2ZiYjM0ZiJ9fX0=";

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault not found! Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!setupPlayerPoints()) {
            getLogger().warning("PlayerPoints not found! Point trading disabled.");
        }
        if (!setupFancyNPC()) {
            getLogger().severe("FancyNPCs not found! Plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            Class.forName("org.bukkit.profile.PlayerProfile");
            isLegacyVersion = false;
        } catch (ClassNotFoundException e) {
            isLegacyVersion = true;
        }

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("fs").setExecutor(this);
        getCommand("fancyshop").setExecutor(this);

        loadConfigs();

        new BukkitRunnable() {
            @Override
            public void run() {
                cacheNpcLocations();
            }
        }.runTaskLater(this, 20L);

        getLogger().info("FancyShop Enhanced Multi-Item has been enabled!");
    }

    @Override
    public void onDisable() {
        saveTrades();
        getLogger().info("FancyShop Enhanced Multi-Item has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupPlayerPoints() {
        if (getServer().getPluginManager().getPlugin("PlayerPoints") == null) {
            return false;
        }
        try {
            playerPointsAPI = PlayerPoints.getInstance().getAPI();
            return playerPointsAPI != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean setupFancyNPC() {
        return getServer().getPluginManager().getPlugin("FancyNpcs") != null;
    }

    private void setupFiles() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        tradesFile = new File(getDataFolder(), "trades.yml");
        guiConfigFile = new File(getDataFolder(), "gui.yml");
        configFile = new File(getDataFolder(), "config.yml");

        if (!tradesFile.exists()) {
            try {
                tradesFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create trades.yml: " + e.getMessage());
            }
        }

        if (!guiConfigFile.exists()) {
            this.saveResource("gui.yml", false);
        }

        if (!configFile.exists()) {
            this.saveResource("config.yml", false);
        }

        tradesConfig = YamlConfiguration.loadConfiguration(tradesFile);
        guiConfig = YamlConfiguration.loadConfiguration(guiConfigFile);
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void loadConfigs() {
        setupFiles();
        tradesConfig = YamlConfiguration.loadConfiguration(tradesFile);
        guiConfig = YamlConfiguration.loadConfiguration(guiConfigFile);
        config = YamlConfiguration.loadConfiguration(configFile);
        loadTrades();
        cacheNpcLocations();
    }

    private void cacheNpcLocations() {
        npcLocations.clear();
        try {
            if (FancyNpcsPlugin.get() != null && FancyNpcsPlugin.get().getNpcManager() != null) {
                Collection<Npc> npcs = FancyNpcsPlugin.get().getNpcManager().getAllNpcs();
                for (Npc npc : npcs) {
                    NpcData data = npc.getData();
                    if (data != null && data.getLocation() != null) {
                        npcLocations.put(data.getId(), data.getLocation());
                    }
                }
                getLogger().info("Cached " + npcLocations.size() + " NPC locations");
            }
        } catch (Exception e) {
            getLogger().warning("Could not cache NPC locations: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("only-players"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("fancyshop.admin")) {
            player.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(getMessage("help-header"));
            player.sendMessage(getMessage("help-edit"));
            player.sendMessage(getMessage("help-reload"));
            player.sendMessage(getMessage("help-debug"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "edit":
                playerCurrentPage.put(player.getUniqueId(), 0);
                openNpcSelectionGUI(player);
                break;
            case "reload":
                loadConfigs();
                player.sendMessage(getMessage("config-reloaded"));
                break;
            case "debug":
                if (args.length > 1) {
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target != null) {
                        debugPlayer(player, target);
                    } else {
                        debugPlayer(player, player);
                    }
                } else {
                    debugPlayer(player, player);
                }
                break;
            default:
                player.sendMessage(getMessage("unknown-command"));
                break;
        }
        return true;
    }

    private String getMessage(String key) {
        return translateColors(config.getString("messages." + key, "&cMessage not found: " + key));
    }

    private String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void debugPlayer(Player sender, Player target) {
        sender.sendMessage(getMessage("debug-header").replace("{player}", target.getName()));
        sender.sendMessage(getMessage("debug-chat-input").replace("{value}", String.valueOf(chatInput.get(target.getUniqueId()))));
        sender.sendMessage(getMessage("debug-editing-npc").replace("{value}", String.valueOf(editingNpc.get(target.getUniqueId()))));
        sender.sendMessage(getMessage("debug-editing-trade").replace("{value}", String.valueOf(editingTradeId.get(target.getUniqueId()))));
        sender.sendMessage(getMessage("debug-current-trade-npc").replace("{value}", String.valueOf(currentTradeNpc.get(target.getUniqueId()))));
        sender.sendMessage(getMessage("debug-current-trade-id").replace("{value}", String.valueOf(currentTradeId.get(target.getUniqueId()))));
        sender.sendMessage(getMessage("debug-current-page").replace("{value}", String.valueOf(playerCurrentPage.getOrDefault(target.getUniqueId(), 0))));
        sender.sendMessage(getMessage("debug-total-npcs").replace("{value}", String.valueOf(npcTrades.size())));
    }

    private void openNpcSelectionGUI(Player player) {
        List<Npc> allNpcs;
        try {
            allNpcs = new ArrayList<>(FancyNpcsPlugin.get().getNpcManager().getAllNpcs());
        } catch (Exception e) {
            player.sendMessage(getMessage("error-loading-npcs"));
            return;
        }

        if (allNpcs.isEmpty()) {
            player.sendMessage(getMessage("no-npcs-found"));
            return;
        }

        String title = translateColors(guiConfig.getString("npc-selection.title", "&9Chọn NPC để chỉnh sửa"));
        int size = guiConfig.getInt("npc-selection.size", 54);
        Inventory gui = Bukkit.createInventory(null, size, title);

        fillDecorative(gui, "npc-selection");

        List<Integer> npcSlots = guiConfig.getIntegerList("npc-selection.npc-slots");
        if (npcSlots.isEmpty()) {
            npcSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }

        int currentPage = playerCurrentPage.getOrDefault(player.getUniqueId(), 0);
        int itemsPerPage = npcSlots.size();
        int totalPages = (int) Math.ceil((double) allNpcs.size() / itemsPerPage);
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allNpcs.size());

        List<Npc> npcsOnPage = allNpcs.subList(startIndex, endIndex);

        int npcIndex = 0;
        for (Npc npc : npcsOnPage) {
            if (npcIndex >= npcSlots.size()) break;
            NpcData npcData = npc.getData();
            if (npcData == null) continue;

            ItemStack item = createConfiguredItem("npc-selection.npc-item", npcData.getName(), npcData.getId());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                for (String line : guiConfig.getStringList("npc-selection.npc-item.lore")) {
                    line = line.replace("{npc_id}", npcData.getId());
                    line = line.replace("{npc_name}", npcData.getName());

                    Map<String, TradeData> npcTradeMap = npcTrades.get(npcData.getId());
                    if (npcTradeMap != null && !npcTradeMap.isEmpty()) {
                        line = line.replace("{status}", guiConfig.getString("npc-selection.status.configured", "&a✓ Đã cấu hình giao dịch"));
                        line = line.replace("{trade_count}", String.valueOf(npcTradeMap.size()));
                    } else {
                        line = line.replace("{status}", guiConfig.getString("npc-selection.status.not-configured", "&c✗ Chưa cấu hình giao dịch"));
                        line = line.replace("{trade_count}", "0");
                    }
                    lore.add(translateColors(line));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            gui.setItem(npcSlots.get(npcIndex++), item);
        }

        if (currentPage > 0) {
            int prevButtonSlot = guiConfig.getInt("npc-selection.pagination.prev-button.slot", 48);
            ItemStack prevButton = createConfiguredItem("npc-selection.pagination.prev-button", "", "");
            gui.setItem(prevButtonSlot, prevButton);
        }

        if (currentPage < totalPages - 1) {
            int nextButtonSlot = guiConfig.getInt("npc-selection.pagination.next-button.slot", 50);
            ItemStack nextButton = createConfiguredItem("npc-selection.pagination.next-button", "", "");
            gui.setItem(nextButtonSlot, nextButton);
        }

        int pageInfoSlot = guiConfig.getInt("npc-selection.pagination.page-info-item.slot", 49);
        ItemStack pageInfoItem = createConfiguredItem("npc-selection.pagination.page-info-item", "", "");
        ItemMeta pageInfoMeta = pageInfoItem.getItemMeta();
        if (pageInfoMeta != null) {
            String displayName = guiConfig.getString("npc-selection.pagination.page-info-item.name", "&e&lTrang {current_page}/{total_pages}");
            displayName = displayName.replace("{current_page}", String.valueOf(currentPage + 1));
            displayName = displayName.replace("{total_pages}", String.valueOf(totalPages));
            pageInfoMeta.setDisplayName(translateColors(displayName));

            List<String> lore = new ArrayList<>();
            for (String line : guiConfig.getStringList("npc-selection.pagination.page-info-item.lore")) {
                line = line.replace("{total_npcs}", String.valueOf(allNpcs.size()));
                lore.add(translateColors(line));
            }
            pageInfoMeta.setLore(lore);
            pageInfoItem.setItemMeta(pageInfoMeta);
        }
        gui.setItem(pageInfoSlot, pageInfoItem);

        player.openInventory(gui);
    }

    private void openNpcTradeManagementGUI(Player player, String npcId) {
        String title = translateColors(guiConfig.getString("npc-trade-management.title", "&6Quản lý giao dịch NPC").replace("{npc_id}", npcId));
        int size = guiConfig.getInt("npc-trade-management.size", 54);
        Inventory gui = Bukkit.createInventory(null, size, title);

        fillDecorative(gui, "npc-trade-management");

        Map<String, TradeData> npcTradeMap = npcTrades.getOrDefault(npcId, new HashMap<>());

        List<Integer> tradeSlots = Arrays.asList(2, 5, 11, 14, 20, 23, 29, 32, 38, 41, 47, 50);

        int slotIndex = 0;
        for (Map.Entry<String, TradeData> entry : npcTradeMap.entrySet()) {
            if (slotIndex >= tradeSlots.size()) break;

            String tradeId = entry.getKey();
            TradeData trade = entry.getValue();

            ItemStack displayItem = createTradeDisplayItem(trade);
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(translateColors("&7Trade ID: &f" + tradeId));
                lore.add(translateColors("&e&lClick trái: &7Chỉnh sửa giao dịch"));
                lore.add(translateColors("&c&lClick phải: &7Xóa giao dịch"));
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }

            gui.setItem(tradeSlots.get(slotIndex++), displayItem);
        }

        if (slotIndex < tradeSlots.size()) {
            int addButtonSlot = guiConfig.getInt("npc-trade-management.add-button.slot", 40);
            ItemStack addButton = createConfiguredItem("npc-trade-management.add-button", "", "");
            gui.setItem(addButtonSlot, addButton);
        }

        int backSlot = guiConfig.getInt("npc-trade-management.back-button.slot", 4);
        ItemStack backButton = createConfiguredItem("npc-trade-management.back-button", "", "");
        gui.setItem(backSlot, backButton);

        editingNpc.put(player.getUniqueId(), npcId);
        player.openInventory(gui);
    }

    private void openTradeTypeSelectionGUI(Player player, String npcId, String tradeId) {
        String title = translateColors(guiConfig.getString("trade-type-selection.title", "&6Chọn loại giao dịch"));
        int size = guiConfig.getInt("trade-type-selection.size", 27);
        Inventory gui = Bukkit.createInventory(null, size, title);

        fillDecorative(gui, "trade-type-selection");

        int itemTradeSlot = guiConfig.getInt("trade-type-selection.item-trade.slot", 11);
        ItemStack itemTradeButton = createConfiguredItem("trade-type-selection.item-trade", "", "");
        gui.setItem(itemTradeSlot, itemTradeButton);

        int moneyTradeSlot = guiConfig.getInt("trade-type-selection.money-trade.slot", 13);
        ItemStack moneyTradeButton = createConfiguredItem("trade-type-selection.money-trade", "", "");
        gui.setItem(moneyTradeSlot, moneyTradeButton);

        int pointTradeSlot = guiConfig.getInt("trade-type-selection.point-trade.slot", 15);
        ItemStack pointTradeButton = createConfiguredItem("trade-type-selection.point-trade", "", "");
        gui.setItem(pointTradeSlot, pointTradeButton);

        editingNpc.put(player.getUniqueId(), npcId);
        editingTradeId.put(player.getUniqueId(), tradeId != null ? tradeId : UUID.randomUUID().toString());
        player.openInventory(gui);
    }

    private void openTradeEditGUI(Player player, String npcId, String tradeId) {
        String title = translateColors(guiConfig.getString("trade-edit.title", "&2Chỉnh sửa giao dịch"));
        Inventory gui = Bukkit.createInventory(null, 54, title);
        fillDecorative(gui, "trade-edit");

        TradeData trade = npcTrades.computeIfAbsent(npcId, k -> new HashMap<>()).computeIfAbsent(tradeId, k -> new TradeData());

        List<Integer> requiredItemSlots = guiConfig.getIntegerList("trade-edit.required-item-slots");
        if (trade.requiredItems != null) {
            for (int i = 0; i < Math.min(trade.requiredItems.size(), requiredItemSlots.size()); i++) {
                gui.setItem(requiredItemSlots.get(i), trade.requiredItems.get(i));
            }
        }

        List<Integer> rewardSlots = guiConfig.getIntegerList("trade-edit.reward-item-slots");
        if (trade.rewardItems != null) {
            for (int i = 0; i < Math.min(trade.rewardItems.size(), rewardSlots.size()); i++) {
                gui.setItem(rewardSlots.get(i), trade.rewardItems.get(i));
            }
        }

        ItemStack reqMoneyHead = createGoldHead(trade.requiredMoney, 0);
        gui.setItem(guiConfig.getInt("trade-edit.required-money-slot"), reqMoneyHead != null ? reqMoneyHead : createConfiguredItem("trade-edit.add-money-item", "", ""));

        ItemStack reqPointHead = createPointHead(trade.requiredPoints);
        gui.setItem(guiConfig.getInt("trade-edit.required-points-slot"), reqPointHead != null ? reqPointHead : createConfiguredItem("trade-edit.add-points-item", "", ""));

        ItemStack rewardMoneyHead = createGoldHead(trade.rewardMoney, 0);
        gui.setItem(guiConfig.getInt("trade-edit.reward-money-slot"), rewardMoneyHead != null ? rewardMoneyHead : createConfiguredItem("trade-edit.add-money-item", "", ""));

        ItemStack rewardPointHead = createPointHead(trade.rewardPoints);
        gui.setItem(guiConfig.getInt("trade-edit.reward-points-slot"), rewardPointHead != null ? rewardPointHead : createConfiguredItem("trade-edit.add-points-item", "", ""));

        gui.setItem(guiConfig.getInt("trade-edit.save-button.slot"), createConfiguredItem("trade-edit.save-button", "", ""));
        gui.setItem(guiConfig.getInt("trade-edit.back-button.slot"), createConfiguredItem("trade-edit.back-button", "", ""));

        editingNpc.put(player.getUniqueId(), npcId);
        editingTradeId.put(player.getUniqueId(), tradeId);
        player.openInventory(gui);
    }
    private void setupMoneyTradeGUI(Inventory gui, TradeData trade) {
        ItemStack moneyHead = createMoneyHead(trade.requiredMoney);
        int moneySlot = guiConfig.getInt("trade-edit.money-trade.money-slot", 12);
        gui.setItem(moneySlot, moneyHead);

        if (trade.rewardItems != null) {
            List<Integer> rewardSlots = guiConfig.getIntegerList("trade-edit.money-trade.reward-slots");
            if (rewardSlots.isEmpty()) {
                rewardSlots = Arrays.asList(14, 23, 32);
            }
            for (int i = 0; i < Math.min(trade.rewardItems.size(), rewardSlots.size()); i++) {
                if (trade.rewardItems.get(i) != null) {
                    gui.setItem(rewardSlots.get(i), trade.rewardItems.get(i));
                }
            }
        }

        if (trade.rewardPoints > 0) {
            ItemStack pointHead = createPointHead(trade.rewardPoints);
            int pointSlot = guiConfig.getInt("trade-edit.money-trade.point-slot", 16);
            gui.setItem(pointSlot, pointHead);
        }
    }

    private void setupPointTradeGUI(Inventory gui, TradeData trade) {
        ItemStack pointHead = createPointHead(trade.requiredPoints);
        int pointSlot = guiConfig.getInt("trade-edit.point-trade.point-slot", 12);
        gui.setItem(pointSlot, pointHead);

        if (trade.rewardItems != null) {
            List<Integer> rewardSlots = guiConfig.getIntegerList("trade-edit.point-trade.reward-slots");
            if (rewardSlots.isEmpty()) {
                rewardSlots = Arrays.asList(14, 23, 32);
            }
            for (int i = 0; i < Math.min(trade.rewardItems.size(), rewardSlots.size()); i++) {
                if (trade.rewardItems.get(i) != null) {
                    gui.setItem(rewardSlots.get(i), trade.rewardItems.get(i));
                }
            }
        }

        if (trade.rewardMoney > 0) {
            ItemStack moneyHead = createMoneyHead(trade.rewardMoney);
            int moneySlot = guiConfig.getInt("trade-edit.point-trade.money-slot", 16);
            gui.setItem(moneySlot, moneyHead);
        }
    }


    private void openTradeGUI(Player player, String npcId, String tradeId) {
        Map<String, TradeData> npcTradeMap = npcTrades.get(npcId);
        if (npcTradeMap == null) return;

        TradeData trade = npcTradeMap.get(tradeId);
        if (trade == null) return;

        String title = translateColors(guiConfig.getString("player-trade-gui.title", "&9&lTrao Đổi"));
        int size = guiConfig.getInt("player-trade-gui.size", 54);
        Inventory gui = Bukkit.createInventory(null, size, title);

        fillDecorative(gui, "player-trade-gui");

        boolean canTrade = true;

        List<Integer> requiredSlots = guiConfig.getIntegerList("player-trade-gui.required-slots");
        if (requiredSlots.isEmpty()) {
            requiredSlots = Arrays.asList(11, 20, 29, 38);
        }

        if (trade.requiredItems != null) {
            for (int i = 0; i < Math.min(trade.requiredItems.size(), requiredSlots.size()); i++) {
                ItemStack requiredItem = trade.requiredItems.get(i);
                if (requiredItem == null || requiredItem.getType() == Material.AIR) {
                    continue;
                }

                ItemStack displayItem = requiredItem.clone();
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    int playerCount = countItems(player, displayItem);
                    int requiredCount = displayItem.getAmount();
                    if (playerCount < requiredCount) {
                        canTrade = false;
                    }

                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    String trackerFormat = getMessage("trade-tracker-format");
                    trackerFormat = trackerFormat.replace("{player_count}", String.valueOf(playerCount))
                            .replace("{required_count}", String.valueOf(requiredCount));
                    lore.add(trackerFormat);

                    String status = playerCount >= requiredCount ?
                            getMessage("trade-can-afford") : getMessage("trade-cannot-afford");
                    lore.add(status);

                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                gui.setItem(requiredSlots.get(i), displayItem);
            }
        }

        if (trade.requiredMoney > 0) {
            if (!economy.has(player, trade.requiredMoney)) {
                canTrade = false;
            }
            ItemStack moneyHead = createGoldHead(trade.requiredMoney, 0);
            ItemMeta meta = moneyHead.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                String status = economy.has(player, trade.requiredMoney) ?
                        getMessage("trade-can-afford") : getMessage("trade-cannot-afford");
                lore.add(status);
                meta.setLore(lore);
                moneyHead.setItemMeta(meta);
            }

            for (int slot : requiredSlots) {
                if (gui.getItem(slot) == null || gui.getItem(slot).getType() == Material.AIR) {
                    gui.setItem(slot, moneyHead);
                    break;
                }
            }
        }

        if (trade.requiredPoints > 0) {
            if (playerPointsAPI == null || playerPointsAPI.look(player.getUniqueId()) < trade.requiredPoints) {
                canTrade = false;
            }
            ItemStack pointHead = createPointHead(trade.requiredPoints);
            ItemMeta meta = pointHead.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                String status = (playerPointsAPI != null && playerPointsAPI.look(player.getUniqueId()) >= trade.requiredPoints) ?
                        getMessage("trade-can-afford") : getMessage("trade-cannot-afford");
                lore.add(status);
                meta.setLore(lore);
                pointHead.setItemMeta(meta);
            }

            for (int slot : requiredSlots) {
                if (gui.getItem(slot) == null || gui.getItem(slot).getType() == Material.AIR) {
                    gui.setItem(slot, pointHead);
                    break;
                }
            }
        }

        List<Integer> rewardSlots = guiConfig.getIntegerList("player-trade-gui.reward-slots");
        if (rewardSlots.isEmpty()) {
            rewardSlots = Arrays.asList(15, 24, 33, 42);
        }

        if (trade.rewardItems != null) {
            for (int i = 0; i < Math.min(trade.rewardItems.size(), rewardSlots.size()); i++) {
                ItemStack rewardItem = trade.rewardItems.get(i);
                if (rewardItem == null || rewardItem.getType() == Material.AIR) {
                    continue;
                }

                ItemStack displayItem = rewardItem.clone();
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");
                    String status = canTrade ? getMessage("trade-reward-available") : getMessage("trade-reward-unavailable");
                    lore.add(status);
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                gui.setItem(rewardSlots.get(i), displayItem);
            }
        }

        if (trade.rewardMoney > 0) {
            ItemStack moneyHead = createGoldHead(trade.rewardMoney, 0);
            ItemMeta meta = moneyHead.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(getMessage("reward-money-title").replace("{amount}", String.valueOf(trade.rewardMoney)));
                List<String> lore = Arrays.asList(
                        getMessage("reward-money-lore").replace("{amount}", String.valueOf(trade.rewardMoney)),
                        "",
                        canTrade ? getMessage("trade-reward-available") : getMessage("trade-reward-unavailable")
                );
                meta.setLore(lore);
                moneyHead.setItemMeta(meta);
            }

            for (int slot : rewardSlots) {
                if (gui.getItem(slot) == null || gui.getItem(slot).getType() == Material.AIR) {
                    gui.setItem(slot, moneyHead);
                    break;
                }
            }
        }

        if (trade.rewardPoints > 0) {
            ItemStack pointHead = createPointHead(trade.rewardPoints);
            ItemMeta meta = pointHead.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(getMessage("reward-points-title").replace("{amount}", String.valueOf(trade.rewardPoints)));
                List<String> lore = Arrays.asList(
                        getMessage("reward-points-lore").replace("{amount}", String.valueOf(trade.rewardPoints)),
                        "",
                        canTrade ? getMessage("trade-reward-available") : getMessage("trade-reward-unavailable")
                );
                meta.setLore(lore);
                pointHead.setItemMeta(meta);
            }

            for (int slot : rewardSlots) {
                if (gui.getItem(slot) == null || gui.getItem(slot).getType() == Material.AIR) {
                    gui.setItem(slot, pointHead);
                    break;
                }
            }
        }

        int tradeButtonSlot = guiConfig.getInt("player-trade-gui.trade-button.slot", 49);
        ItemStack tradeButton = new ItemStack(Material.EMERALD_BLOCK);

        if (canTrade) {
            String materialName = guiConfig.getString("player-trade-gui.trade-button.material-can-trade", "EMERALD_BLOCK");
            try {
                tradeButton.setType(Material.valueOf(materialName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                tradeButton.setType(Material.EMERALD_BLOCK);
            }
        } else {
            String materialName = guiConfig.getString("player-trade-gui.trade-button.material-cannot-trade", "REDSTONE_BLOCK");
            try {
                tradeButton.setType(Material.valueOf(materialName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                tradeButton.setType(Material.REDSTONE_BLOCK);
            }
        }

        ItemMeta buttonMeta = tradeButton.getItemMeta();
        if (buttonMeta != null) {
            if (canTrade) {
                buttonMeta.setDisplayName(getMessage("trade-button-confirm"));
                List<String> lore = Arrays.asList(getMessage("trade-button-confirm-lore"));
                buttonMeta.setLore(lore);
            } else {
                buttonMeta.setDisplayName(translateColors("&c&l✗ KHÔNG THỂ GIAO DỊCH"));
                List<String> lore = Arrays.asList(translateColors("&7Bạn không đủ điều kiện!"));
                buttonMeta.setLore(lore);
            }
            tradeButton.setItemMeta(buttonMeta);
        }
        gui.setItem(tradeButtonSlot, tradeButton);

        currentTradeNpc.put(player.getUniqueId(), npcId);
        currentTradeId.put(player.getUniqueId(), tradeId);

        player.openInventory(gui);
    }


    private int countItems(Player player, ItemStack item) {
        if (item == null) {
            return 0;
        }
        int count = 0;
        for (ItemStack playerItem : player.getInventory().getContents()) {
            if (playerItem != null && playerItem.isSimilar(item)) {
                count += playerItem.getAmount();
            }
        }
        return count;
    }





    public ItemStack createGoldHead(double money, int points) {
        String title;
        List<String> lore;

        if (money > 0) {
            title = getMessage("currency-money-title");
            lore = Collections.singletonList(getMessage("currency-money-amount").replace("{amount}", String.format("%,.0f", money)));
        } else if (points > 0) {
            title = getMessage("currency-points-title");
            lore = Collections.singletonList(getMessage("currency-points-amount").replace("{amount}", String.valueOf(points)));
        } else {
            return null;
        }

        return createCustomHead(GOLD_HEAD_TEXTURE, title, lore);
    }


    private ItemStack createCustomHead(String textureValue, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        meta.setDisplayName(translateColors(displayName));
        if (lore != null) {
            List<String> coloredLore = new ArrayList<>();
            for (String line : lore) {
                coloredLore.add(translateColors(line));
            }
            meta.setLore(coloredLore);
        }

        applyTexture(meta, textureValue);
        head.setItemMeta(meta);
        return head;
    }
    public ItemStack createPointHead(int points) {
        if (points <= 0) {
            return null;
        }
        String title = getMessage("currency-points-title");
        List<String> lore = Collections.singletonList(getMessage("currency-points-amount").replace("{amount}", String.valueOf(points)));

        return createCustomHead(POINT_HEAD_TEXTURE, title, lore);
    }

    private ItemStack createMoneyHead(double amount) {
        String title = getMessage("currency-money-title").replace("{amount}", String.valueOf(amount));
        List<String> lore = Arrays.asList(
                getMessage("currency-money-amount").replace("{amount}", String.valueOf(amount))
        );

        return createCustomHead(GOLD_HEAD_TEXTURE, title, lore);
    }
    public static Inventory getTopInventory(InventoryEvent event) {
        try {
            Object view = event.getView();
            Method getTopInventory = view.getClass().getMethod("getTopInventory");
            getTopInventory.setAccessible(true);
            return (Inventory) getTopInventory.invoke(view);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    private void applyTexture(SkullMeta meta, String base64) {
        if (isLegacyVersion) {
            try {
                Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
                Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
                Object gameProfile = gameProfileConstructor.newInstance(UUID.randomUUID(), null);
                Method getPropertiesMethod = gameProfile.getClass().getMethod("getProperties");
                Object propertyMap = getPropertiesMethod.invoke(gameProfile);
                Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                Constructor<?> propertyConstructor = propertyClass.getConstructor(String.class, String.class);
                Object textureProperty = propertyConstructor.newInstance("textures", base64);
                Method putMethod = propertyMap.getClass().getMethod("put", Object.class, Object.class);
                putMethod.invoke(propertyMap, "textures", textureProperty);
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, gameProfile);
            } catch (Exception e) {
                getLogger().warning("Failed to apply legacy custom texture to skull: " + e.getMessage());
            }
        } else {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                String decodedJson = new String(Base64.getDecoder().decode(base64));

                int urlStartIndex = decodedJson.indexOf("http");
                if (urlStartIndex == -1) {
                    getLogger().warning("Could not find texture URL in base64 data.");
                    return;
                }
                int urlEndIndex = decodedJson.indexOf("\"", urlStartIndex);
                String textureUrl = decodedJson.substring(urlStartIndex, urlEndIndex);

                textures.setSkin(new URL(textureUrl));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
            } catch (Exception e) {
                getLogger().warning("Failed to apply modern custom texture to skull: " + e.getMessage());
            }
        }
    }

    private void fillDecorative(Inventory gui, String section) {
        ConfigurationSection decorSection = guiConfig.getConfigurationSection(section + ".decorative");
        if (decorSection == null) return;

        for (String key : decorSection.getKeys(false)) {
            ConfigurationSection itemSection = decorSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            List<Integer> slots = itemSection.getIntegerList("slots");
            ItemStack item = createConfiguredItem(section + ".decorative." + key, "", "");
            for (int slot : slots) {
                if (slot >= 0 && slot < gui.getSize()) {
                    gui.setItem(slot, item);
                }
            }
        }
    }

    private ItemStack createConfiguredItem(String path, String npcName, String npcId) {
        ConfigurationSection section = guiConfig.getConfigurationSection(path);
        if (section == null) {
            return new ItemStack(Material.STONE);
        }

        String materialName = section.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String displayName = section.getString("name", "");
            displayName = displayName.replace("{npc_name}", npcName).replace("{npc_id}", npcId);
            meta.setDisplayName(translateColors(displayName));

            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("lore")) {
                line = line.replace("{npc_name}", npcName).replace("{npc_id}", npcId);
                lore.add(translateColors(line));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
    public static String getInventoryTitle(InventoryEvent event) {
        try {
            Object view = event.getView();
            Method getTitleMethod = view.getClass().getMethod("getTitle");
            getTitleMethod.setAccessible(true);

            return (String) getTitleMethod.invoke(view);

        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            Bukkit.getLogger().warning("[YourPluginName] Không thể lấy tiêu đề GUI bằng reflection: " + e.getMessage());
            return "";
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = getInventoryTitle(event);
        UUID playerUUID = player.getUniqueId();

        String npcSelectionTitle = translateColors(guiConfig.getString("npc-selection.title", "&9Chọn NPC để chỉnh sửa"));
        String npcShopTitle = translateColors(guiConfig.getString("npc-shop.title", "&1&lTrao Đổi"));
        String tradeTypeTitle = translateColors(guiConfig.getString("trade-type-selection.title", "&6Chọn loại giao dịch"));
        String editTitleRaw = guiConfig.getString("trade-edit.title", "&2Chỉnh sửa giao dịch");
        String tradeManagementTitleRaw = guiConfig.getString("npc-trade-management.title", "&6&lQuản lý: {npc_id}");

        String tradeManagementTitleBase = translateColors(tradeManagementTitleRaw.split("\\{")[0]);
        if (title.startsWith(tradeManagementTitleBase) && editingNpc.containsKey(playerUUID)) {
            event.setCancelled(true);
            handleNpcTradeManagement(player, event);
            return;
        }

        String editTitleBase = translateColors(editTitleRaw.split(" -")[0]);
        if (title.startsWith(editTitleBase) && editingTradeId.containsKey(playerUUID)) {
            handleTradeEditClick(player, event);
            return;
        }

        if (title.equals(npcSelectionTitle)) {
            event.setCancelled(true);
            handleNpcSelection(player, event);
        } else if (title.equals(npcShopTitle)) {
            event.setCancelled(true);
            handleNpcShopClick(player, event);
        } else if (title.equals(tradeTypeTitle)) {
            event.setCancelled(true);
            handleTradeTypeSelection(player, event);
        }
    }
    private boolean hasRequiredItems(Player player, List<ItemStack> required, int times) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (times <= 0) {
            return true;
        }

        for (ItemStack requiredItem : required) {
            if (requiredItem == null || requiredItem.getType() == Material.AIR) continue;
            int totalRequiredAmount = requiredItem.getAmount() * times;
            if (countItems(player, requiredItem) < totalRequiredAmount) {
                return false;
            }
        }
        return true;
    }

    private void removeItems(Player player, List<ItemStack> toRemove, int times) {
        if (toRemove == null || toRemove.isEmpty() || times <= 0) {
            return;
        }
        for (ItemStack itemToRemove : toRemove) {
            if (itemToRemove != null && itemToRemove.getType() != Material.AIR) {
                ItemStack totalToRemove = itemToRemove.clone();
                totalToRemove.setAmount(itemToRemove.getAmount() * times);
                player.getInventory().removeItem(totalToRemove);
            }
        }
        player.updateInventory();
    }

    private int calculateMaxTrades(Player player, TradeData trade) {
        int maxTrades = Integer.MAX_VALUE;

        if (trade.requiredMoney > 0) {
            if (economy.getBalance(player) < trade.requiredMoney) return 0;
            maxTrades = Math.min(maxTrades, (int) (economy.getBalance(player) / trade.requiredMoney));
        }

        if (trade.requiredPoints > 0) {
            if (playerPointsAPI == null || playerPointsAPI.look(player.getUniqueId()) < trade.requiredPoints) return 0;
            maxTrades = Math.min(maxTrades, playerPointsAPI.look(player.getUniqueId()) / trade.requiredPoints);
        }

        if (trade.requiredItems != null) {
            for (ItemStack requiredItem : trade.requiredItems) {
                if (requiredItem == null || requiredItem.getType() == Material.AIR) continue;
                int playerAmount = countItems(player, requiredItem);
                if (playerAmount < requiredItem.getAmount()) return 0;
                maxTrades = Math.min(maxTrades, playerAmount / requiredItem.getAmount());
            }
        }

        if (maxTrades == Integer.MAX_VALUE) {
            return 1;
        }

        return maxTrades;
    }

    private void handleNpcSelection(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int clickedSlot = event.getSlot();

        int prevButtonSlot = guiConfig.getInt("npc-selection.pagination.prev-button.slot", 48);
        int nextButtonSlot = guiConfig.getInt("npc-selection.pagination.next-button.slot", 50);

        if (clickedSlot == prevButtonSlot) {
            int currentPage = playerCurrentPage.getOrDefault(player.getUniqueId(), 0);
            if (currentPage > 0) {
                playerCurrentPage.put(player.getUniqueId(), currentPage - 1);
                openNpcSelectionGUI(player);
            }
            return;
        }

        if (clickedSlot == nextButtonSlot) {
            int currentPage = playerCurrentPage.getOrDefault(player.getUniqueId(), 0);
            List<Npc> allNpcs = new ArrayList<>(FancyNpcsPlugin.get().getNpcManager().getAllNpcs());
            List<Integer> npcSlots = guiConfig.getIntegerList("npc-selection.npc-slots");
            if (npcSlots.isEmpty()) {
                npcSlots = Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
            }
            int itemsPerPage = npcSlots.size();
            int totalPages = (int) Math.ceil((double) allNpcs.size() / itemsPerPage);
            if (currentPage < totalPages - 1) {
                playerCurrentPage.put(player.getUniqueId(), currentPage + 1);
                openNpcSelectionGUI(player);
            }
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null) return;

        String npcId = null;
        for (String line : meta.getLore()) {
            String cleanLine = ChatColor.stripColor(line);
            if (cleanLine.startsWith("ID: ")) {
                npcId = cleanLine.substring(4);
                break;
            }
        }

        if (npcId != null) {
            openNpcTradeManagementGUI(player, npcId);
        }
    }

    private void handleNpcTradeManagement(Player player, InventoryClickEvent event) {
        int clickedSlot = event.getSlot();
        String npcId = editingNpc.get(player.getUniqueId());
        if (npcId == null) return;

        if (clickedSlot == guiConfig.getInt("npc-trade-management.back-button.slot", 4)) {
            openNpcSelectionGUI(player);
            return;
        }

        if (clickedSlot == guiConfig.getInt("npc-trade-management.add-button.slot", 40)) {
            openTradeEditGUI(player, npcId, UUID.randomUUID().toString());
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        String tradeId = null;
        for (String line : meta.getLore()) {
            String cleanLine = ChatColor.stripColor(line);
            if (cleanLine.startsWith("Trade ID: ")) {
                tradeId = cleanLine.substring(10);
                break;
            }
        }

        if (tradeId != null) {
            if (event.getClick() == ClickType.RIGHT) {
                deleteTrade(player, npcId, tradeId);
            } else {
                openTradeEditGUI(player, npcId, tradeId);
            }
        }
    }
    private void handleNpcShopClick(Player player, InventoryClickEvent event) {
        int clickedSlot = event.getSlot();
        String npcId = currentTradeNpc.get(player.getUniqueId());
        if (npcId == null) return;

        if (clickedSlot % 9 == 7) {
            int row = clickedSlot / 9;
            switch(row) {
                case 0:
                    int currentPage = playerShopPage.getOrDefault(player.getUniqueId(), 0);
                    if (currentPage > 0) {
                        playerShopPage.put(player.getUniqueId(), currentPage - 1);
                        openNpcShopGUI(player, npcId);
                    }
                    break;
                case 2:
                    Map<String, TradeData> trades = npcTrades.get(npcId);
                    int totalPages = (int) Math.ceil((double) trades.size() / TRADES_PER_PAGE);
                    int current = playerShopPage.getOrDefault(player.getUniqueId(), 0);
                    if (current < totalPages - 1) {
                        playerShopPage.put(player.getUniqueId(), current + 1);
                        openNpcShopGUI(player, npcId);
                    }
                    break;
                case 5:
                    player.closeInventory();
                    break;
            }
            return;
        }

        if (clickedSlot % 9 == 5) {
            Map<Integer, String> tradeSlots = playerTradeSlots.get(player.getUniqueId());
            if (tradeSlots != null && tradeSlots.containsKey(clickedSlot)) {
                String tradeId = tradeSlots.get(clickedSlot);
                currentTradeId.put(player.getUniqueId(), tradeId);
                executeTrade(player, event.isShiftClick());
            }
        }
    }
    private void handleTradeTypeSelection(Player player, InventoryClickEvent event) {
        int clickedSlot = event.getSlot();
        String npcId = editingNpc.get(player.getUniqueId());
        String tradeId = editingTradeId.get(player.getUniqueId());
        if (npcId == null || tradeId == null) return;

        int itemTradeSlot = guiConfig.getInt("trade-type-selection.item-trade.slot", 11);
        int moneyTradeSlot = guiConfig.getInt("trade-type-selection.money-trade.slot", 13);
        int pointTradeSlot = guiConfig.getInt("trade-type-selection.point-trade.slot", 15);

        if (clickedSlot == itemTradeSlot) {
            openTradeEditGUI(player, npcId, tradeId);
        } else if (clickedSlot == moneyTradeSlot) {
            openTradeEditGUI(player, npcId, tradeId);
        } else if (clickedSlot == pointTradeSlot) {
            openTradeEditGUI(player, npcId, tradeId);
        }
    }

    private void handleTradeEditClick(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (event.getClickedInventory() == event.getWhoClicked().getInventory()) {
            return;
        }
        event.setCancelled(true);

        String npcId = editingNpc.get(player.getUniqueId());
        String tradeId = editingTradeId.get(player.getUniqueId());
        if (npcId == null || tradeId == null) return;

        TradeData trade = npcTrades.get(npcId).get(tradeId);

        if (slot == guiConfig.getInt("trade-edit.save-button.slot")) {
            saveTrade(player, event.getClickedInventory());
            return;
        }
        if (slot == guiConfig.getInt("trade-edit.back-button.slot")) {
            openNpcTradeManagementGUI(player, npcId);
            return;
        }

        String inputType = null;
        if (slot == guiConfig.getInt("trade-edit.required-money-slot")) inputType = "required_money";
        else if (slot == guiConfig.getInt("trade-edit.required-points-slot")) inputType = "required_points";
        else if (slot == guiConfig.getInt("trade-edit.reward-money-slot")) inputType = "reward_money";
        else if (slot == guiConfig.getInt("trade-edit.reward-points-slot")) inputType = "reward_points";

        if (inputType != null) {
            if (event.getClick() == ClickType.RIGHT) {
                switch(inputType) {
                    case "required_money": trade.requiredMoney = 0; break;
                    case "required_points": trade.requiredPoints = 0; break;
                    case "reward_money": trade.rewardMoney = 0; break;
                    case "reward_points": trade.rewardPoints = 0; break;
                }
                openTradeEditGUI(player, npcId, tradeId);
            } else {
                chatInput.put(player.getUniqueId(), inputType);
                player.closeInventory();
                player.sendMessage(getMessage(inputType.contains("money") ? "currency-enter-money-amount" : "currency-enter-points-amount"));
            }
            return;
        }

        List<Integer> editableSlots = new ArrayList<>();
        editableSlots.addAll(guiConfig.getIntegerList("trade-edit.required-item-slots"));
        editableSlots.addAll(guiConfig.getIntegerList("trade-edit.reward-item-slots"));

        if (editableSlots.contains(slot)) {
            event.setCancelled(false);
        }
    }





    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String input = chatInput.get(player.getUniqueId());
        if (input == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();
        String npcId = editingNpc.get(player.getUniqueId());
        String tradeId = editingTradeId.get(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            chatInput.remove(player.getUniqueId());
            player.sendMessage(getMessage("currency-cancelled"));
            if (npcId != null && tradeId != null) {
                Bukkit.getScheduler().runTask(this, () -> openTradeEditGUI(player, npcId, tradeId));
            }
            return;
        }

        if (npcId == null || tradeId == null) {
            chatInput.remove(player.getUniqueId());
            return;
        }

        TradeData trade = npcTrades.get(npcId).get(tradeId);
        if (trade == null) {
            chatInput.remove(player.getUniqueId());
            return;
        }

        try {
            if (input.endsWith("money")) {
                double amount = Double.parseDouble(message);
                if (amount < 0) {
                    player.sendMessage(getMessage("currency-negative-amount"));
                    return;
                }
                switch (input) {
                    case "required_money": trade.requiredMoney = amount; break;
                    case "reward_money": trade.rewardMoney = amount; break;
                }
            } else if (input.endsWith("points")) {
                int amount = Integer.parseInt(message);
                if (amount < 0) {
                    player.sendMessage(getMessage("currency-negative-amount"));
                    return;
                }
                switch (input) {
                    case "required_points": trade.requiredPoints = amount; break;
                    case "reward_points": trade.rewardPoints = amount; break;
                }
            }

            chatInput.remove(player.getUniqueId());
            player.sendMessage(getMessage("currency-set-" + input.replace("_", "-")).replace("{amount}", message));
            Bukkit.getScheduler().runTask(this, () -> openTradeEditGUI(player, npcId, tradeId));

        } catch (NumberFormatException e) {
            player.sendMessage(getMessage("currency-invalid-number"));
        }
    }

    private void executeTrade(Player player, boolean isShiftClick) {
        String npcId = currentTradeNpc.get(player.getUniqueId());
        String tradeId = currentTradeId.get(player.getUniqueId());
        if (npcId == null || tradeId == null) {
            player.sendMessage(getMessage("error-trade-data-not-found"));
            player.closeInventory();
            return;
        }
        TradeData trade = npcTrades.get(npcId).get(tradeId);
        if (trade == null) {
            player.sendMessage(getMessage("error-trade-not-exist"));
            player.closeInventory();
            return;
        }

        int tradesToPerform = isShiftClick ? calculateMaxTrades(player, trade) : 1;

        if (tradesToPerform == 0) {
            player.sendMessage(getMessage("trade-insufficient-items"));
            return;
        }

        if (!hasRequiredItems(player, trade.requiredItems, tradesToPerform)) {
            player.sendMessage(getMessage("trade-insufficient-items"));
            return;
        }
        if (trade.requiredMoney > 0 && economy.getBalance(player) < trade.requiredMoney * tradesToPerform) {
            player.sendMessage(getMessage("trade-insufficient-money").replace("{amount}", String.valueOf(trade.requiredMoney * tradesToPerform)));
            return;
        }
        if (trade.requiredPoints > 0 && (playerPointsAPI == null || playerPointsAPI.look(player.getUniqueId()) < trade.requiredPoints * tradesToPerform)) {
            player.sendMessage(getMessage("trade-insufficient-points").replace("{amount}", String.valueOf(trade.requiredPoints * tradesToPerform)));
            return;
        }

        removeItems(player, trade.requiredItems, tradesToPerform);
        if (trade.requiredMoney > 0) economy.withdrawPlayer(player, trade.requiredMoney * tradesToPerform);
        if (trade.requiredPoints > 0) playerPointsAPI.take(player.getUniqueId(), trade.requiredPoints * tradesToPerform);

        if (trade.rewardItems != null) {
            for(int i = 0; i < tradesToPerform; i++) {
                for (ItemStack item : trade.rewardItems) {
                    if(item != null) {
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                        if (!leftover.isEmpty()) {
                            for (ItemStack drop : leftover.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                            }
                            player.sendMessage(getMessage("trade-inventory-full"));
                        }
                    }
                }
            }
        }
        if (trade.rewardMoney > 0) economy.depositPlayer(player, trade.rewardMoney * tradesToPerform);
        if (trade.rewardPoints > 0) playerPointsAPI.give(player.getUniqueId(), trade.rewardPoints * tradesToPerform);

        if (isShiftClick && tradesToPerform > 1) {
            player.sendMessage(getMessage("trade-completed-multiple").replace("{times}", String.valueOf(tradesToPerform)));
        } else if (tradesToPerform > 0) {
            player.sendMessage(getMessage("trade-completed"));
        }

        openNpcShopGUI(player, npcId);
    }

    private boolean hasRequiredItems(Player player, List<ItemStack> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }

        List<ItemStack> tempInventory = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                tempInventory.add(item.clone());
            }
        }

        for (ItemStack requiredItem : required) {
            if (requiredItem == null) continue;
            int amountStillNeeded = requiredItem.getAmount();

            for (ItemStack tempItem : tempInventory) {
                if (amountStillNeeded > 0 && tempItem != null && tempItem.getAmount() > 0 && tempItem.isSimilar(requiredItem)) {
                    int amountToTake = Math.min(amountStillNeeded, tempItem.getAmount());
                    tempItem.setAmount(tempItem.getAmount() - amountToTake);
                    amountStillNeeded -= amountToTake;
                }
            }

            if (amountStillNeeded > 0) {
                return false;
            }
        }

        return true;
    }

    private void removeItems(Player player, List<ItemStack> toRemove) {
        for (ItemStack removeItem : toRemove) {
            if (removeItem != null) {
                player.getInventory().removeItem(removeItem);
            }
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(NpcInteractEvent event) {
        try {
            Player player = event.getPlayer();
            Npc npc = event.getNpc();
            if (npc == null || npc.getData() == null) return;

            String npcId = npc.getData().getId();
            Map<String, TradeData> npcTradeMap = npcTrades.get(npcId);

            if (npcTradeMap != null && !npcTradeMap.isEmpty()) {
                event.setCancelled(true);
                openNpcShopGUI(player, npcId);
            }
        } catch (Exception e) {

        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();
        Location clickedLocation = clickedEntity.getLocation();

        for (Map.Entry<String, Location> entry : npcLocations.entrySet()) {
            Location npcLoc = entry.getValue();
            if (npcLoc.getWorld().equals(clickedLocation.getWorld()) &&
                    npcLoc.distance(clickedLocation) < 2.0) {
                String npcId = entry.getKey();
                Map<String, TradeData> npcTradeMap = npcTrades.get(npcId);

                if (npcTradeMap != null && !npcTradeMap.isEmpty()) {
                    event.setCancelled(true);
                    openNpcShopGUI(player, npcId);
                    break;
                }
            }
        }
    }

    private void openNpcShopGUI(Player player, String npcId) {
        Map<String, TradeData> npcTradeMap = npcTrades.get(npcId);
        if (npcTradeMap == null || npcTradeMap.isEmpty()) {
            player.sendMessage(getMessage("shop-no-trades"));
            return;
        }

        String title = translateColors(guiConfig.getString("npc-shop.title", "&1&lTrao Đổi"));
        Inventory gui = Bukkit.createInventory(player, 54, title);
        fillDecorative(gui, "npc-shop");

        List<String> sortedTradeIds = new ArrayList<>(npcTradeMap.keySet());
        Collections.sort(sortedTradeIds);

        int currentPage = playerShopPage.getOrDefault(player.getUniqueId(), 0);
        int totalTrades = sortedTradeIds.size();
        int totalPages = (int) Math.ceil((double) totalTrades / TRADES_PER_PAGE);

        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1;
            playerShopPage.put(player.getUniqueId(), currentPage);
        }

        int startIndex = currentPage * TRADES_PER_PAGE;
        int endIndex = Math.min(startIndex + TRADES_PER_PAGE, totalTrades);

        Map<Integer, String> tradeSlotsForPlayer = new HashMap<>();
        int lastDrawnRow = -1;

        for (int i = startIndex; i < endIndex; i++) {
            String tradeId = sortedTradeIds.get(i);
            int row = i % TRADES_PER_PAGE;
            drawTradeRow(gui, player, npcTradeMap.get(tradeId), row);
            tradeSlotsForPlayer.put(5 + (row * 9), tradeId);
            lastDrawnRow = row;
        }
        playerTradeSlots.put(player.getUniqueId(), tradeSlotsForPlayer);

        ItemStack filler = createConfiguredItem("npc-shop.empty-trade-slot", "", "");
        for (int row = lastDrawnRow + 1; row < TRADES_PER_PAGE; row++) {
            int startSlot = row * 9;
            gui.setItem(startSlot + 2, filler);
            gui.setItem(startSlot + 3, filler);
            gui.setItem(startSlot + 4, filler);
            gui.setItem(startSlot + 5, filler);
        }

        drawControlPanel(gui, player, currentPage, totalPages);

        currentTradeNpc.put(player.getUniqueId(), npcId);
        player.openInventory(gui);
    }
    private void drawTradeRow(Inventory gui, Player player, TradeData trade, int row) {
        int startSlot = row * 9;

        List<ItemStack> required = new ArrayList<>();
        if (trade.requiredItems != null) {
            trade.requiredItems.stream()
                    .filter(i -> i != null && i.getType() != Material.AIR)
                    .forEach(required::add);
        }

        ItemStack reqMoneyHead = createGoldHead(trade.requiredMoney, 0);
        if (reqMoneyHead != null) {
            required.add(reqMoneyHead);
        }

        ItemStack reqPointHead = createPointHead(trade.requiredPoints);
        if (reqPointHead != null) {
            required.add(reqPointHead);
        }

        if (!required.isEmpty()) gui.setItem(startSlot + 2, required.get(0));
        if (required.size() > 1) gui.setItem(startSlot + 3, required.get(1));

        ItemStack arrow = createConfiguredItem("npc-shop.arrow-item", "", "");
        gui.setItem(startSlot + 4, arrow);

        ItemStack rewardItem = createTradeDisplayItem(trade);
        gui.setItem(startSlot + 5, rewardItem);
    }
    private void drawControlPanel(Inventory gui, Player player, int currentPage, int totalPages) {
        int lastColumn = 7;

        if (currentPage > 0) {
            gui.setItem(lastColumn, createConfiguredItem("npc-shop.control-panel.prev-page", "", ""));
        } else {
            gui.setItem(lastColumn, createConfiguredItem("npc-shop.control-panel.placeholder", "", ""));
        }

        ItemStack pageInfo = createConfiguredItem("npc-shop.control-panel.page-info", "", "");
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        if(pageInfoMeta != null) {
            String name = pageInfoMeta.getDisplayName()
                    .replace("{current_page}", String.valueOf(currentPage + 1))
                    .replace("{total_pages}", String.valueOf(totalPages == 0 ? 1 : totalPages));
            pageInfoMeta.setDisplayName(translateColors(name));
            pageInfo.setItemMeta(pageInfoMeta);
        }
        gui.setItem(lastColumn + 9, pageInfo);

        if (currentPage < totalPages - 1) {
            gui.setItem(lastColumn + 18, createConfiguredItem("npc-shop.control-panel.next-page", "", ""));
        } else {
            gui.setItem(lastColumn + 18, createConfiguredItem("npc-shop.control-panel.placeholder", "", ""));
        }

        ItemStack playerInfo = createConfiguredItem("npc-shop.control-panel.player-info", player.getName(), "");
        ItemMeta playerInfoMeta_ = playerInfo.getItemMeta();
        if(playerInfoMeta_ instanceof SkullMeta) {
            ((SkullMeta) playerInfoMeta_).setOwningPlayer(player);
        }
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        lore.add(translateColors(getMessage("shop-player-money").replace("{amount}", String.format("%,.0f", economy.getBalance(player)))));
        if (playerPointsAPI != null) {
            lore.add(translateColors(getMessage("shop-player-points").replace("{amount}", String.format("%,d", playerPointsAPI.look(player.getUniqueId())))));
        }
        playerInfoMeta_.setLore(lore);
        playerInfo.setItemMeta(playerInfoMeta_);
        gui.setItem(lastColumn + 27, playerInfo);

        gui.setItem(lastColumn + 36, createConfiguredItem("npc-shop.control-panel.help", "", ""));
        gui.setItem(lastColumn + 45, createConfiguredItem("npc-shop.control-panel.close", "", ""));
    }
    private ItemStack createTradeDisplayItem(TradeData trade) {
        if (trade.rewardItems != null && !trade.rewardItems.isEmpty()) {
            ItemStack displayItem = null;
            for (ItemStack item : trade.rewardItems) {
                if (item != null && !item.getType().isAir()) {
                    displayItem = item.clone();
                    break;
                }
            }

            if (displayItem == null) {
            } else {
                ItemMeta meta = displayItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add(" ");

                    int otherItemCount = -1;
                    for (ItemStack item : trade.rewardItems) {
                        if (item != null && !item.getType().isAir()) {
                            otherItemCount++;
                        }
                    }

                    if (trade.rewardMoney > 0) {
                        lore.add(getMessage("shop-extra-reward-money").replace("{amount}", String.format("%,.0f", trade.rewardMoney)));
                    }
                    if (trade.rewardPoints > 0) {
                        lore.add(getMessage("shop-extra-reward-points").replace("{amount}", String.format("%,d", trade.rewardPoints)));
                    }
                    if (otherItemCount > 0) {
                        lore.add(getMessage("shop-extra-reward-items").replace("{count}", String.valueOf(otherItemCount)));
                    }

                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);
                }
                return displayItem;
            }
        }

        if (trade.rewardMoney > 0) {
            String title = getMessage("shop-reward-money-title").replace("{amount}", String.format("%,.0f", trade.rewardMoney));
            List<String> lore = new ArrayList<>();
            lore.add(getMessage("shop-reward-money-lore").replace("{amount}", String.format("%,.0f", trade.rewardMoney)));
            if (trade.rewardPoints > 0) {
                lore.add(getMessage("shop-extra-reward-points").replace("{amount}", String.format("%,d", trade.rewardPoints)));
            }
            return createCustomHead(GOLD_HEAD_TEXTURE, title, lore);
        }

        if (trade.rewardPoints > 0) {
            String title = getMessage("shop-reward-points-title").replace("{amount}", String.format("%,d", trade.rewardPoints));
            List<String> lore = Collections.singletonList(getMessage("shop-reward-points-lore").replace("{amount}", String.format("%,d", trade.rewardPoints)));
            return createCustomHead(POINT_HEAD_TEXTURE, title, lore);
        }

        if (trade.requiredMoney > 0) {
            return createGoldHead(trade.requiredMoney, 0);
        }
        if (trade.requiredPoints > 0) {
            return createPointHead(trade.requiredPoints);
        }
        if (trade.requiredItems != null && !trade.requiredItems.isEmpty()) {
            for (ItemStack item : trade.requiredItems) {
                if (item != null && !item.getType().isAir()) {
                    return item.clone();
                }
            }
        }

        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(translateColors("&c&lGiao dịch trống"));
            barrierMeta.setLore(Collections.singletonList(translateColors("&7Giao dịch này chưa được cấu hình")));
            barrier.setItemMeta(barrierMeta);
        }
        return barrier;
    }


    private void deleteTrade(Player player, String npcId, String tradeId) {
        Map<String, TradeData> npcTradeMap = npcTrades.get(npcId);
        if (npcTradeMap != null) {
            npcTradeMap.remove(tradeId);
            if (npcTradeMap.isEmpty()) {
                npcTrades.remove(npcId);
            }
            saveTrades();
            player.sendMessage(getMessage("trade-deleted"));
            openNpcTradeManagementGUI(player, npcId);
        }
    }



    private void saveTrade(Player player, Inventory gui) {
        String npcId = editingNpc.get(player.getUniqueId());
        String tradeId = editingTradeId.get(player.getUniqueId());
        if (npcId == null || tradeId == null) return;

        TradeData trade = npcTrades.get(npcId).get(tradeId);
        trade.requiredItems = new ArrayList<>();
        trade.rewardItems = new ArrayList<>();
        ItemStack placeholder = createConfiguredItem("trade-edit.placeholder-item", "", "");

        List<Integer> requiredSlots = guiConfig.getIntegerList("trade-edit.required-item-slots");
        for(int slot : requiredSlots) {
            ItemStack item = gui.getItem(slot);
            if(item != null && !item.isSimilar(placeholder) && !item.getType().isAir()) {
                trade.requiredItems.add(item.clone());
            }
        }

        List<Integer> rewardSlots = guiConfig.getIntegerList("trade-edit.reward-item-slots");
        for(int slot : rewardSlots) {
            ItemStack item = gui.getItem(slot);
            if(item != null && !item.isSimilar(placeholder) && !item.getType().isAir()) {
                trade.rewardItems.add(item.clone());
            }
        }

        saveTrades();
        player.sendMessage(getMessage("trade-saved"));
        player.closeInventory();
        editingNpc.remove(player.getUniqueId());
        editingTradeId.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        String closedTitle = getInventoryTitle(event);
        String shopTitle = translateColors(guiConfig.getString("npc-shop.title", "&1&lTrao Đổi"));

        if (closedTitle.equals(shopTitle)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }

                    String currentTitle = getInventoryTitle(event);

                    if (!currentTitle.equals(shopTitle)) {
                        currentTradeNpc.remove(playerUUID);
                        currentTradeId.remove(playerUUID);
                        playerShopPage.remove(playerUUID);
                        playerTradeSlots.remove(playerUUID);
                    }
                }
            }.runTaskLater(FancyShop.this, 1L);
        }
    }
    private void loadTrades() {
        npcTrades.clear();
        if (!tradesConfig.contains("trades")) return;

        ConfigurationSection tradesSection = tradesConfig.getConfigurationSection("trades");
        if (tradesSection == null) return;

        for (String npcId : tradesSection.getKeys(false)) {
            ConfigurationSection npcSection = tradesSection.getConfigurationSection(npcId);
            if (npcSection == null) continue;

            Map<String, TradeData> npcTradeMap = new HashMap<>();

            for (String tradeId : npcSection.getKeys(false)) {
                ConfigurationSection tradeSection = npcSection.getConfigurationSection(tradeId);
                if (tradeSection == null) continue;

                TradeData trade = new TradeData();
                trade.requiredMoney = tradeSection.getDouble("requiredMoney", 0);
                trade.requiredPoints = tradeSection.getInt("requiredPoints", 0);
                trade.rewardMoney = tradeSection.getDouble("rewardMoney", 0);
                trade.rewardPoints = tradeSection.getInt("rewardPoints", 0);

                if (tradeSection.contains("requiredItems")) {
                    trade.requiredItems = (List<ItemStack>) tradeSection.getList("requiredItems");
                }
                if (tradeSection.contains("rewardItems")) {
                    trade.rewardItems = (List<ItemStack>) tradeSection.getList("rewardItems");
                }

                npcTradeMap.put(tradeId, trade);
            }

            if (!npcTradeMap.isEmpty()) {
                npcTrades.put(npcId, npcTradeMap);
            }
        }

        getLogger().info("Loaded trades for " + npcTrades.size() + " NPCs");
    }

    private void saveTrades() {
        tradesConfig.set("trades", null);

        for (Map.Entry<String, Map<String, TradeData>> npcEntry : npcTrades.entrySet()) {
            String npcId = npcEntry.getKey();
            Map<String, TradeData> npcTradeMap = npcEntry.getValue();

            for (Map.Entry<String, TradeData> tradeEntry : npcTradeMap.entrySet()) {
                String tradeId = tradeEntry.getKey();
                TradeData trade = tradeEntry.getValue();

                String path = "trades." + npcId + "." + tradeId;
                tradesConfig.set(path + ".requiredMoney", trade.requiredMoney);
                tradesConfig.set(path + ".requiredPoints", trade.requiredPoints);
                tradesConfig.set(path + ".rewardMoney", trade.rewardMoney);
                tradesConfig.set(path + ".rewardPoints", trade.rewardPoints);

                if (trade.requiredItems != null) {
                    tradesConfig.set(path + ".requiredItems", trade.requiredItems);
                }
                if (trade.rewardItems != null) {
                    tradesConfig.set(path + ".rewardItems", trade.rewardItems);
                }
            }
        }

        try {
            tradesConfig.save(tradesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class TradeData {
        List<ItemStack> requiredItems;
        double requiredMoney;
        int requiredPoints;
        List<ItemStack> rewardItems;
        double rewardMoney;
        int rewardPoints;

        public TradeData() {
            this.requiredMoney = 0;
            this.requiredPoints = 0;
            this.rewardMoney = 0;
            this.rewardPoints = 0;
        }
    }
}


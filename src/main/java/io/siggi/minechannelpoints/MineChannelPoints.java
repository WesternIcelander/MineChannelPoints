package io.siggi.minechannelpoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.siggi.cubecore.CubeCore;
import io.siggi.cubecore.apiserver.ApiContext;
import io.siggi.cubecore.apiserver.ApiServer;
import io.siggi.http.HTTPRequest;
import io.siggi.http.session.Session;
import io.siggi.minechannelpoints.twitchapi.TokenResponse;
import io.siggi.minechannelpoints.twitchapi.TwitchApi;
import io.siggi.minechannelpoints.util.Util;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import static io.siggi.minechannelpoints.util.Util.getString;

public class MineChannelPoints extends JavaPlugin implements Listener {
    private static MineChannelPoints instance;
    private static final JsonParser jsonParser = new JsonParser();
    private TwitchApi twitchApi;
    private static final String API_PREFIX = "/minechannelpoints";

    public static MineChannelPoints getInstance() {
        return instance;
    }

    public static TwitchApi getTwitchApi() {
        return instance.twitchApi;
    }

    public boolean blockSelfRedeems = true;

    public final List<ChannelPointPlayer> players = new ArrayList<>();

    private int highRandomTickRateSecondsLeft = 0;

    public ChannelPointPlayer getPlayer(UUID uuid, boolean createIfDoesntExist) {
        ChannelPointPlayer player = players.stream().filter(p -> p.getMinecraftUuid().equals(uuid)).findFirst().orElse(null);
        if (player == null && createIfDoesntExist) {
            player = new ChannelPointPlayer(uuid, null, null, null);
            players.add(player);
        }
        return player;
    }

    public ChannelPointPlayer load(UUID uuid) {
        try {
            File playerFile = new File(getDataFolder(), "players/" + uuid.toString() + ".json");
            JsonObject playerData;
            try (FileReader reader = new FileReader(playerFile)) {
                playerData = JsonParser.parseReader(reader).getAsJsonObject();
            }
            String twitchId = getString(playerData.get("twitchId"));
            String authToken = getString(playerData.get("authToken"));
            String refreshToken = getString(playerData.get("refreshToken"));
            ChannelPointPlayer player = new ChannelPointPlayer(uuid, twitchId, authToken, refreshToken);
            JsonObject rewards = playerData.getAsJsonObject("currentRewards");
            if (rewards != null) {
                for (Map.Entry<String, JsonElement> entry : rewards.asMap().entrySet()) {
                    String rewardId = entry.getKey();
                    String rewardType = getString(entry.getValue());
                    if (rewardId == null || rewardType == null) continue;
                    ChannelPointReward reward = ChannelPointRewards.get(rewardType);
                    player.getCurrentRewards().put(rewardId, reward);
                }
            }
            return player;
        } catch (Exception e) {
            return null;
        }
    }

    public void save(ChannelPointPlayer player) {
        try {
            File playerFile = new File(getDataFolder(), "players/" + player.getMinecraftUuid().toString() + ".json");
            File save = new File(getDataFolder(), "players/" + player.getMinecraftUuid().toString() + ".json.sav");
            File playerFileParent = playerFile.getParentFile();
            if (!playerFileParent.exists()) playerFileParent.mkdirs();
            JsonObject playerData = new JsonObject();
            playerData.addProperty("twitchId", player.getTwitchId());
            playerData.addProperty("authToken", player.directGetAuthToken());
            playerData.addProperty("refreshToken", player.getRefreshToken());
            JsonObject currentRewards = new JsonObject();
            playerData.add("currentRewards", currentRewards);
            for (Map.Entry<String, ChannelPointReward> entry : player.getCurrentRewards().entrySet()) {
                currentRewards.addProperty(entry.getKey(), entry.getValue().id());
            }
            try (FileWriter writer = new FileWriter(save)) {
                writer.write(playerData.toString());
            }
            save.renameTo(playerFile);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, e, () -> "Unable to save player data for " + player.getMinecraftUuid());
        }
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        File configFile = new File(getDataFolder(), "config.json");
        JsonObject config = null;
        try (FileReader reader = new FileReader(configFile)) {
            config = jsonParser.parse(reader).getAsJsonObject();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, e, () -> "Unable to load config");
            setEnabled(false);
            return;
        }
        try {
            CubeCore.forceAllowMethod("PATCH");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, t, () -> "Unable to force HttpURLConnection to allow PATCH method.");
            setEnabled(false);
            return;
        }
        ApiServer apiServer = CubeCore.getApiServer();
        if (apiServer == null) {
            getLogger().log(Level.SEVERE, () -> "ApiServer is not available.");
            setEnabled(false);
            return;
        }
        JsonElement blockSelfRedeemsElement = config.get("blockSelfRedeems");
        if (blockSelfRedeemsElement != null) {
            blockSelfRedeems = blockSelfRedeemsElement.getAsBoolean();
        }
        String clientId = config.get("clientId").getAsString();
        String clientSecret = config.get("clientSecret").getAsString();
        String eventSubSecret = config.get("eventSubSecret").getAsString();
        String publicEndpoint = apiServer.getPublicEndpoint();
        if (publicEndpoint == null) {
            getLogger().log(Level.SEVERE, () -> "Public Endpoint not configured for CubeCore ApiServer");
            setEnabled(false);
            return;
        }
        publicEndpoint += API_PREFIX;
        apiServer.addHandler(API_PREFIX, this::handleHttpRequest);

        File playersDirectory = new File(getDataFolder(), "players");
        File[] files = playersDirectory.listFiles();
        if (files != null) for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".json")) continue;
            try {
                UUID uuid = UUID.fromString(name.substring(0, name.length() - 5));
                ChannelPointPlayer player = load(uuid);
                players.add(player);
            } catch (Exception ignored) {
            }
        }

        twitchApi = new TwitchApi(this, clientId, clientSecret, eventSubSecret, publicEndpoint);
        twitchApi.start();

        ChannelPointsCommand cpCommand = new ChannelPointsCommand(this, publicEndpoint + "/login");
        PluginCommand cpPluginCommand = getCommand("channelpoints");
        cpPluginCommand.setExecutor(cpCommand);
        cpPluginCommand.setTabCompleter(cpCommand);

        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (highRandomTickRateSecondsLeft > 0) {
                    highRandomTickRateSecondsLeft -= 1;
                    if (highRandomTickRateSecondsLeft == 0) {
                        for (World world : Bukkit.getWorlds()) {
                            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
                        }
                        announce("Fast growth has ended!");
                    } else if (highRandomTickRateSecondsLeft % 60 == 0) {
                        int minutesLeft = highRandomTickRateSecondsLeft / 60;
                        announce(minutesLeft + " minute" + (minutesLeft == 1 ? "" : "s") + " remaining for fast growth!");
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    @Override
    public void onDisable() {
        if (twitchApi != null) {
            twitchApi.stop();
        }
        if (highRandomTickRateSecondsLeft > 0) {
            highRandomTickRateSecondsLeft = 0;
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
            }
        }
        for (ChannelPointPlayer player : players) {
            save(player);
        }
    }

    public void addFastGrowthTime(int seconds) {
        if (highRandomTickRateSecondsLeft == 0) {
            for (World world : Bukkit.getWorlds()) {
                world.setGameRule(GameRule.RANDOM_TICK_SPEED, 300);
            }
        }
        highRandomTickRateSecondsLeft += seconds;
    }

    private final Map<String, Long> recentNotifications = new HashMap<>();
    private final Map<String, LoginCode> loginCodes = new HashMap<>();

    private void handleHttpRequest(HTTPRequest request, ApiContext apiContext) throws Exception {
        Session session = request.getSessionIfExists();
        UUID minecraftUuid = null;
        ChannelPointPlayer player = null;
        if (session != null) {
            String minecraftUuidString = session.get("minecraft-uuid");
            minecraftUuid = minecraftUuidString == null ? null : UUID.fromString(minecraftUuidString);
            player = getPlayer(minecraftUuid, false);
        }

        String endpoint = request.url.substring(API_PREFIX.length());
        if (endpoint.equals("/twitch-notification")) {
            String messageType = request.getHeader("Twitch-Eventsub-Message-Type");
            if (messageType == null) return;
            byte[] requestData = Util.readFully(request.inStream);
            if (messageType.equalsIgnoreCase("webhook_callback_verification")) {
                JsonObject object = JsonParser.parseString(new String(requestData, StandardCharsets.UTF_8)).getAsJsonObject();
                byte[] challengeResponse = object.get("challenge").getAsString().getBytes(StandardCharsets.UTF_8);
                request.response.setContentType("text/plain");
                request.response.contentLength(challengeResponse.length);
                request.response.write(challengeResponse);
                return;
            }
            try {
                String signature = request.getHeader("Twitch-Eventsub-Message-Signature");
                SecretKeySpec secretKeySpec = new SecretKeySpec(twitchApi.getEventSubSecret().getBytes(), "HmacSHA256");
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(secretKeySpec);
                mac.update(request.getHeader("Twitch-Eventsub-Message-Id").getBytes(StandardCharsets.UTF_8));
                mac.update(request.getHeader("Twitch-Eventsub-Message-Timestamp").getBytes(StandardCharsets.UTF_8));
                String expectedSignature = "sha256=" + Util.bytesToHex(mac.doFinal(requestData));
                if (!expectedSignature.equals(signature)) {
                    throw new Exception("Bad Signature");
                }
            } catch (Exception e) {
                request.response.setHeader("403 Forbidden");
                request.response.contentLength(0);
                request.response.write("");
                return;
            }
            request.response.write("");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            String theFuckingTimestamp = request.getHeader("Twitch-Eventsub-Message-Timestamp");
            theFuckingTimestamp = theFuckingTimestamp.substring(0, 19) + "Z";
            long messageTimestamp = sdf.parse(theFuckingTimestamp).getTime();
            if (messageTimestamp < System.currentTimeMillis() - 600000L) {
                // If the message is older than 10 minutes drop it.
                return;
            }
            synchronized (recentNotifications) {
                long now = System.currentTimeMillis();
                recentNotifications.entrySet().removeIf((e) -> {
                    Long time = e.getValue();
                    return time == null || now - time > 1200000L;
                });
                String messageId = request.getHeader("Twitch-Eventsub-Message-Id");
                Long time = recentNotifications.get(messageId);
                if (time != null) {
                    // This is a duplicate notification, do not process it.
                    return;
                }
                recentNotifications.put(messageId, now);
            }
            JsonObject notificationObject = JsonParser.parseString(new String(requestData, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject eventObject = notificationObject.get("event").getAsJsonObject();
            JsonObject rewardObject = eventObject.get("reward").getAsJsonObject();
            String broadcasterId = eventObject.get("broadcaster_user_id").getAsString();
            String redeemerId = eventObject.get("user_id").getAsString();
            String redeemerName = eventObject.get("user_name").getAsString();
            String redemptionId = eventObject.get("id").getAsString();
            String rewardId = rewardObject.get("id").getAsString();
            int cost = rewardObject.get("cost").getAsInt();
            JsonElement userInputJson = eventObject.get("user_input");
            String userInput = userInputJson == null || userInputJson.isJsonNull() ? null : userInputJson.getAsString();
            new BukkitRunnable() {
                @Override
                public void run() {
                    handleChannelPointRedemption(broadcasterId, redeemerId, redeemerName, redemptionId, rewardId, cost, userInput);
                }
            }.runTask(this);
        } else if (endpoint.equals("/twitch-callback")) {
            if (minecraftUuid == null) {
                return;
            }
            String code = request.get.get("code");
            if (code == null) {
                return;
            }
            TokenResponse tokenResponse;
            try {
                tokenResponse = twitchApi.initialAuthorization(code).get(15L, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                return;
            }
            if (tokenResponse == null) {
                return;
            }
            player = getPlayer(minecraftUuid, true);
            player.storeAuthTokenResponse(tokenResponse);
            player.setTwitchId(twitchApi.getBroadcasterId(player).get());
            twitchApi.ensureCorrectSubscriptions();
            request.response.redirect(API_PREFIX + "/manage");
        } else if (endpoint.equals("/login")) {
            String code = request.get.get("code");
            if (code == null) return;
            LoginCode loginCode = loginCodes.remove(code);
            if (loginCode == null || loginCode.expiry() < System.currentTimeMillis()) return;
            session = request.getSession();
            session.set("minecraft-uuid", loginCode.minecraftUuid().toString());
            request.response.redirect(API_PREFIX + "/manage");
        } else if (endpoint.equals("/info.json")) {
            if (player == null) {
                return;
            }
            String xsrfToken = session.get("xsrfToken");
            if (xsrfToken == null) {
                session.set("xsrfToken", xsrfToken = UUID.randomUUID().toString().replace("-", ""));
            }
            JsonObject info = new JsonObject();
            SortedSet<ChannelPointReward> rewards = new TreeSet<>(Comparator.comparing(ChannelPointReward::name));
            rewards.addAll(ChannelPointRewards.get().values());
            JsonArray allRewards = new JsonArray();
            info.add("rewards", allRewards);
            for (ChannelPointReward reward : rewards) {
                JsonObject rewardObject = new JsonObject();
                allRewards.add(rewardObject);
                rewardObject.addProperty("id", reward.id());
                rewardObject.addProperty("name", reward.name());
                rewardObject.addProperty("description", reward.description());
                rewardObject.addProperty("stringInput", reward.stringInput());
                rewardObject.addProperty("minimumCost", reward.minimumCost());
            }
            List<String> allRewardIds = new ArrayList<>();
            JsonObject currentRewards = new JsonObject();
            info.add("currentRewards", currentRewards);
            for (Map.Entry<String, ChannelPointReward> entry : player.getCurrentRewards().entrySet()) {
                currentRewards.addProperty(entry.getKey(), entry.getValue().id());
                allRewardIds.add(entry.getKey());
            }
            boolean shouldSave = false;
            try {
                List<String> idsNotIncluded = new ArrayList<>(allRewardIds);
                List<JsonObject> twitchRewards = twitchApi.getExistingRewards(player).get(15, TimeUnit.SECONDS);
                JsonArray twitchRewardsArray = new JsonArray();
                info.add("twitchRewards", twitchRewardsArray);
                for (JsonObject twitchReward : twitchRewards) {
                    twitchRewardsArray.add(twitchReward);
                    String id = twitchReward.get("id").getAsString();
                    idsNotIncluded.remove(id);
                }
                for (String idNotIncluded : idsNotIncluded) {
                    player.getCurrentRewards().remove(idNotIncluded);
                    currentRewards.remove(idNotIncluded);
                    shouldSave = true;
                }
            } catch (Exception e) {
            }
            if (shouldSave) {
                save(player);
            }
            info.addProperty("xsrfToken", xsrfToken);
            request.response.setContentType("application/json");
            request.response.write(info.toString());
        } else if (endpoint.equals("/addreward")) {
            if (player == null) return;
            String correctXsrfToken = session.get("xsrfToken");
            String rewardId = request.post.get("reward");
            String xsrfToken = request.post.get("xsrfToken");
            JsonObject result = new JsonObject();
            if (correctXsrfToken == null || !correctXsrfToken.equals(xsrfToken)) {
                result.addProperty("error", "Incorrect xsrfToken");
                return;
            } else {
                ChannelPointReward channelPointReward = ChannelPointRewards.get(rewardId);
                String id = twitchApi.addCustomReward(player, channelPointReward).get(15, TimeUnit.SECONDS);
                if (id != null) {
                    result.addProperty("id", id);
                    player.getCurrentRewards().put(id, channelPointReward);
                    save(player);
                } else {
                    result.addProperty("error", "Could not add reward");
                }
            }
            request.response.setContentType("application/json");
            request.response.write(result.toString());
        } else if (endpoint.equals("/manage")) {
            if (minecraftUuid == null) {
                return;
            }
            if (player == null || player.getRefreshToken() == null || player.getTwitchId() == null) {
                request.response.redirect(twitchApi.getInitialUrl());
                return;
            }
            returnFile(request, "manage.html", "text/html; charset=utf-8");
        } else if (endpoint.equals("/style.css")) {
            returnFile(request, "style.css", "text/css");
        } else if (endpoint.equals("/cp.js")) {
            returnFile(request, "cp.js", "text/javascript");
        }
    }

    private void returnFile(HTTPRequest request, String file, String contentType) throws Exception {
        request.response.setContentType(contentType);
        request.response.cache(300);
        InputStream in = MineChannelPoints.class.getResourceAsStream("/webcontent/" + file);
        if (in != null) {
            try (InputStream i = in) {
                Util.copy(i, request.response);
            }
        }
    }

    private void handleChannelPointRedemption(String broadcasterId, String redeemerId, String redeemerName, String redemptionId, String rewardId, int cost, String userInput) {
        ChannelPointPlayer player = players.stream().filter(p -> p.getTwitchId().equals(broadcasterId)).findFirst().orElse(null);
        if (player == null) return;
        Map<String, ChannelPointReward> currentRewards = player.getCurrentRewards();
        ChannelPointReward channelPointReward = currentRewards.get(rewardId);
        if (channelPointReward == null) return;
        ChannelPointAction action = channelPointReward.action();
        Player gamePlayer = Bukkit.getPlayer(player.getMinecraftUuid());
        if (gamePlayer == null) {
            twitchApi.rejectRedemption(player, rewardId, redemptionId);
            return;
        }
        if (cost < channelPointReward.minimumCost()) {
            twitchApi.rejectRedemption(player, rewardId, redemptionId);
            announce(redeemerName + " tried to redeem " + channelPointReward.name() + " on " + gamePlayer.getName() + "'s channel, but the price of " + cost + " is set below the minimum allowed which is " + channelPointReward.minimumCost() + ". The channel points were automatically refunded to the viewer.");
            return;
        }
        if (blockSelfRedeems && redeemerId.equals(broadcasterId)) {
            twitchApi.rejectRedemption(player, rewardId, redemptionId);
            announce(redeemerName + " tried to redeem " + channelPointReward.name() + " on their own channel which is not allowed.");
            return;
        }
        if (action.run(gamePlayer, redeemerName, userInput)) {
            twitchApi.completeRedemption(player, rewardId, redemptionId);
        } else {
            twitchApi.rejectRedemption(player, rewardId, redemptionId);
        }
    }

    public static void announce(String message) {
        announce(new TextComponent(message));
    }

    public static void announce(BaseComponent message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(message);
        }
    }

    public static void give(Player player, ItemStack item) {
        // We drop the item onto the ground with no pickup delay, and only allowing the intended target to pick it up
        // This prevents the item getting lost if the target player's inventory is full
        Item worldItem = player.getWorld().dropItem(player.getLocation(), item);
        worldItem.setUnlimitedLifetime(true);
        worldItem.setOwner(player.getUniqueId());
        worldItem.setPickupDelay(0);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player gamePlayer = event.getPlayer();
        ChannelPointPlayer player = players.stream().filter(p -> p.getMinecraftUuid().equals(gamePlayer.getUniqueId())).findFirst().orElse(null);
        if (player == null) return;
        if (player.getTwitchId() == null || player.getRefreshToken() == null) {
            gamePlayer.sendMessage("Your Twitch account was disconnected. You need to connect it again for channel point rewards to work again.");
            return;
        }
        twitchApi.ensureCorrectSubscriptions();
    }

    public LoginCode generateLoginCode(UUID minecraftUuid) {
        loginCodes.values().removeIf(next -> next.expiry() < System.currentTimeMillis());
        String code = UUID.randomUUID().toString().replace("-", "");
        LoginCode loginCode = new LoginCode(code, minecraftUuid, System.currentTimeMillis() + 30000L);
        loginCodes.put(code, loginCode);
        return loginCode;
    }
}

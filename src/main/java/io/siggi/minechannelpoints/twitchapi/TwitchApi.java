package io.siggi.minechannelpoints.twitchapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.siggi.minechannelpoints.ChannelPointPlayer;
import io.siggi.minechannelpoints.ChannelPointReward;
import io.siggi.minechannelpoints.MineChannelPoints;
import io.siggi.minechannelpoints.twitchapi.actions.Action;
import io.siggi.minechannelpoints.twitchapi.actions.AppAction;
import io.siggi.minechannelpoints.twitchapi.actions.UserAction;
import io.siggi.minechannelpoints.util.MapBuilder;
import io.siggi.minechannelpoints.util.UrlUtils;
import io.siggi.minechannelpoints.util.Util;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static io.siggi.minechannelpoints.util.Util.getString;

@SuppressWarnings("deprecation")
public class TwitchApi {
    private final JsonParser jsonParser = new JsonParser();
    private final MineChannelPoints plugin;
    private final String clientId;
    private final String clientSecret;
    private final String eventSubSecret;
    private final String apiEndpoint;
    private final String callbackEndpoint;
    private final String twitchNotificationEndpoint;
    private String appToken;

    private boolean started = false;
    private boolean stopped = false;

    public String getEventSubSecret() {
        return eventSubSecret;
    }

    public TwitchApi(MineChannelPoints plugin, String clientId, String clientSecret, String eventSubSecret, String apiEndpoint) {
        this.plugin = plugin;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.eventSubSecret = eventSubSecret;
        this.apiEndpoint = apiEndpoint;
        this.callbackEndpoint = apiEndpoint + "/twitch-callback";
        this.twitchNotificationEndpoint = apiEndpoint + "/twitch-notification";
    }

    public String getInitialUrl() {
        return "https://id.twitch.tv/oauth2/authorize?" + UrlUtils.urlEncodeMap(
                new MapBuilder<String, String>()
                        .add("client_id", clientId)
                        .add("redirect_uri", callbackEndpoint)
                        .add("response_type", "code")
                        .add("scope", "channel:manage:redemptions")
                        .toMap()
        );
    }

    private HttpURLConnection connectUnauthenticated(String url) throws IOException {
        plugin.getLogger().log(Level.INFO, () -> "Calling API " + url);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestProperty("User-Agent", "MineChannelPoints");
        return connection;
    }

    private HttpURLConnection connect(String url) throws IOException {
        HttpURLConnection connection = connectUnauthenticated(url);
        connection.setRequestProperty("Authorization", "Bearer " + getAppAccessToken());
        connection.setRequestProperty("Client-Id", clientId);
        return connection;
    }

    private HttpURLConnection connect(ChannelPointPlayer player, String url) throws IOException, TokenUnavailableException {
        HttpURLConnection connection = connectUnauthenticated(url);
        connection.setRequestProperty("Authorization", "Bearer " + player.getAuthToken());
        connection.setRequestProperty("Client-Id", clientId);
        return connection;
    }

    private HttpURLConnection post(HttpURLConnection connection, Map<String, String> data) throws IOException {
        String request = UrlUtils.urlEncodeMap(data);
        byte[] requestBody = request.getBytes(StandardCharsets.UTF_8);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Size", Integer.toString(requestBody.length));
        connection.setFixedLengthStreamingMode(requestBody.length);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(requestBody);
        return connection;
    }

    private HttpURLConnection post(HttpURLConnection connection, JsonObject object) throws IOException {
        return post(connection, object, "POST");
    }

    private HttpURLConnection patch(HttpURLConnection connection, JsonObject object) throws IOException {
        return post(connection, object, "PATCH");
    }

    private HttpURLConnection post(HttpURLConnection connection, JsonObject object, String method) throws IOException {
        String request = object.toString();
        byte[] requestBody = request.getBytes(StandardCharsets.UTF_8);
        connection.setRequestMethod(method);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Size", Integer.toString(requestBody.length));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(requestBody.length);
        OutputStream outputStream = connection.getOutputStream();
        outputStream.write(requestBody);
        return connection;
    }

    public void invalidateAppAccessToken() {
        appToken = null;
    }

    private String getAppAccessToken() throws IOException {
        if (appToken == null) {
            HttpURLConnection connection = post(
                    connectUnauthenticated("https://id.twitch.tv/oauth2/token"),
                    new MapBuilder<String, String>()
                            .add("client_id", clientId)
                            .add("client_secret", clientSecret)
                            .add("grant_type", "client_credentials")
                            .toMap()
            );
            JsonObject object = jsonParser.parse(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
            appToken = object.get("access_token").getAsString();
        }
        return appToken;
    }

    public CompletableFuture<TokenResponse> initialAuthorization(String code) {
        CompletableFuture<TokenResponse> future = new CompletableFuture<>();
        queueAction(new AppAction((p) -> {
            try {
                future.complete(doInitialAuthorization(code));
            } catch (TokenUnavailableException e) {
                future.completeExceptionally(e);
            }
        }));
        return future;
    }

    private TokenResponse doInitialAuthorization(String code) throws IOException, TokenUnavailableException {
        HttpURLConnection connection = post(
                connectUnauthenticated("https://id.twitch.tv/oauth2/token"),
                new MapBuilder<String, String>()
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("code", code)
                        .add("grant_type", "authorization_code")
                        .add("redirect_uri", callbackEndpoint)
                        .toMap()
        );
        if (connection.getResponseCode() / 100 == 4) {
            throw new TokenUnavailableException();
        }
        JsonObject object = jsonParser.parse(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
        String accessToken = object.get("access_token").getAsString();
        String refreshToken = object.get("refresh_token").getAsString();
        return new TokenResponse(accessToken, refreshToken);
    }

    public TokenResponse refreshAuthorization(String code) throws IOException, TokenUnavailableException {
        HttpURLConnection connection = post(
                connectUnauthenticated("https://id.twitch.tv/oauth2/token"),
                new MapBuilder<String, String>()
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .add("refresh_token", code)
                        .add("grant_type", "refresh_token")
                        .toMap()
        );
        if (connection.getResponseCode() / 100 == 4) {
            throw new TokenUnavailableException();
        }
        JsonObject object = jsonParser.parse(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
        String accessToken = object.get("access_token").getAsString();
        String refreshToken = object.get("refresh_token").getAsString();
        return new TokenResponse(accessToken, refreshToken);
    }

    public CompletableFuture<String> getBroadcasterId(ChannelPointPlayer player) {
        CompletableFuture<String> future = new CompletableFuture<>();
        queueAction(new UserAction(player, p -> {
            try {
                future.complete(doGetBroadcasterId(p));
            } catch (Exception e) {
                future.completeExceptionally(e);
                throw e;
            }
        }));
        return future;
    }

    private String doGetBroadcasterId(ChannelPointPlayer player) throws IOException, TokenUnavailableException, ForbiddenException, UnauthorizedException {
        HttpURLConnection connection = connect(player, "https://id.twitch.tv/oauth2/validate");
        getResponseCode(connection);
        JsonObject rootObject = parseJson(connection).getAsJsonObject();
        return rootObject.get("user_id").getAsString();
    }

    public int getResponseCode(HttpURLConnection connection) throws ForbiddenException, UnauthorizedException, IOException {
        int responseCode = connection.getResponseCode();
        if (responseCode == 401) {
            throw new UnauthorizedException();
        }
        if (responseCode == 403) {
            throw new ForbiddenException();
        }
        return responseCode;
    }

    public void start() {
        if (started) return;
        started = true;
        (runLoop = new Thread(this::run)).start();
    }

    public void stop() {
        stopped = true;
        runLoop.interrupt();
    }

    private Thread runLoop = null;
    private final List<Action> queuedActions = new LinkedList<>();

    private void run() {
        Action action = null;
        while (!stopped) {
            try {
                synchronized (queuedActions) {
                    if (action != null) {
                        queuedActions.add(action);
                        action = null;
                    }
                    while (!stopped && queuedActions.isEmpty()) queuedActions.wait();
                    if (stopped) break;
                    action = queuedActions.remove(0);
                }
                if (action != null) {
                    if (action.run())
                        action = null;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, e, () -> "A problem occurred in an action");
            }
        }
    }

    public void queueAction(Action action) {
        synchronized (queuedActions) {
            queuedActions.add(action);
            queuedActions.notifyAll();
        }
    }

    private JsonElement parseJson(HttpURLConnection connection) throws IOException, ForbiddenException, UnauthorizedException {
        getResponseCode(connection);
        return JsonParser.parseReader(new InputStreamReader(connection.getInputStream()));
    }

    private List<JsonObject> getEventSubSubscriptions() throws IOException, ForbiddenException, UnauthorizedException {
        List<JsonObject> subs = new ArrayList<>();
        String next = null;
        while (true) {
            HttpURLConnection connection = connect("https://api.twitch.tv/helix/eventsub/subscriptions?" +
                    UrlUtils.urlEncodeMap(new MapBuilder<String, String>()
                            .add("first", "100")
                            .add("after", next)
                            .toMap()));
            JsonObject object = parseJson(connection).getAsJsonObject();
            JsonArray data = object.get("data").getAsJsonArray();
            for (JsonElement element : data) {
                subs.add(element.getAsJsonObject());
            }
            JsonElement pagination = object.get("pagination");
            if (pagination == null) break;
            JsonElement cursorElement = pagination.getAsJsonObject().get("cursor");
            if (cursorElement == null) break;
            next = cursorElement.getAsString();
        }
        return subs;
    }

    private int addSubscription(String broadcasterId, String type, String version) throws IOException, ForbiddenException, UnauthorizedException {
        JsonObject condition = new JsonObject();
        condition.addProperty("broadcaster_user_id", broadcasterId);
        JsonObject transport = new JsonObject();
        transport.addProperty("method", "webhook");
        transport.addProperty("callback", twitchNotificationEndpoint);
        transport.addProperty("secret", eventSubSecret);
        JsonObject create = new JsonObject();
        create.add("condition", condition);
        create.add("transport", transport);
        create.addProperty("type", type);
        create.addProperty("version", version);
        HttpURLConnection connection = post(connect("https://api.twitch.tv/helix/eventsub/subscriptions"), create);
        return getResponseCode(connection);
    }

    private int deleteSubscription(String id) throws IOException, ForbiddenException, UnauthorizedException {
        HttpURLConnection connection = connect("https://api.twitch.tv/helix/eventsub/subscriptions?" +
                UrlUtils.urlEncodeMap(new MapBuilder<String, String>()
                        .add("id", id)
                        .toMap())
        );
        connection.setRequestMethod("DELETE");
        return getResponseCode(connection);
    }

    public CompletableFuture<String> addCustomReward(ChannelPointPlayer player, ChannelPointReward reward) {
        CompletableFuture<String> future = new CompletableFuture<>();
        queueAction(new UserAction(player, (p) -> {
            try {
                future.complete(createCustomReward(player, reward));
            } catch (ForbiddenException | TokenUnavailableException e) {
                future.completeExceptionally(e);
                throw e;
            }
        }));
        return future;
    }

    private String createCustomReward(ChannelPointPlayer player, ChannelPointReward reward) throws IOException, ForbiddenException, UnauthorizedException, TokenUnavailableException {
        HttpURLConnection connection = connect(player, "https://api.twitch.tv/helix/channel_points/custom_rewards?" +
                UrlUtils.urlEncodeMap(new MapBuilder<String, String>()
                        .add("broadcaster_id", player.getTwitchId())
                        .toMap())
        );
        JsonObject redemptionData = new JsonObject();
        redemptionData.addProperty("title", reward.name());
        redemptionData.addProperty("cost", reward.minimumCost());
        redemptionData.addProperty("prompt", reward.description());
        redemptionData.addProperty("is_user_input_required", reward.stringInput());
        post(connection, redemptionData);
        if (getResponseCode(connection) / 100 == 2) {
            JsonObject result = parseJson(connection).getAsJsonObject();
            JsonObject data = result.getAsJsonArray("data").get(0).getAsJsonObject();
            return data.get("id").getAsString();
        } else {
            return null;
        }
    }

    public CompletableFuture<Void> removeCustomReward(ChannelPointPlayer player, String rewardId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        queueAction(new UserAction(player, (p) -> {
            try {
                int result = deleteCustomReward(player, rewardId);
                if (result / 100 != 2) {
                    future.completeExceptionally(new Exception("Error " + result));
                }
                future.complete(null);
            } catch (ForbiddenException | TokenUnavailableException e) {
                future.completeExceptionally(e);
                throw e;
            }
        }));
        return future;
    }

    private int deleteCustomReward(ChannelPointPlayer player, String rewardId) throws IOException, ForbiddenException, UnauthorizedException, TokenUnavailableException {
        HttpURLConnection connection = connect(player, "https://api.twitch.tv/helix/channel_points/custom_rewards?" +
                UrlUtils.urlEncodeMap(new MapBuilder<String, String>()
                        .add("broadcaster_id", player.getTwitchId())
                        .add("id", rewardId)
                        .toMap())
        );
        connection.setRequestMethod("DELETE");
        return getResponseCode(connection);
    }

    private List<JsonObject> doGetExistingRewards(ChannelPointPlayer player) throws IOException, ForbiddenException, UnauthorizedException, TokenUnavailableException {
        HttpURLConnection connection = connect(player, "https://api.twitch.tv/helix/channel_points/custom_rewards?" +
                UrlUtils.urlEncodeMap(new MapBuilder<String, String>()
                        .add("broadcaster_id", player.getTwitchId())
                        .add("only_manageable_rewards", "true")
                        .toMap())
        );
        getResponseCode(connection);
        JsonObject rootObject = parseJson(connection).getAsJsonObject();
        JsonArray data = rootObject.getAsJsonArray("data");
        List<JsonObject> objects = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            objects.add(data.get(i).getAsJsonObject());
        }
        return objects;
    }

    public CompletableFuture<List<JsonObject>> getExistingRewards(ChannelPointPlayer player) {
        CompletableFuture<List<JsonObject>> future = new CompletableFuture<>();
        queueAction(new UserAction(player, (p) -> {
            try {
                future.complete(doGetExistingRewards(p));
            } catch (ForbiddenException | TokenUnavailableException e) {
                future.completeExceptionally(e);
                throw e;
            }
        }));
        return future;
    }

    public void ensureCorrectSubscriptions() {
        queueAction(new AppAction((p) -> {
            List<JsonObject> eventSubSubscriptions = getEventSubSubscriptions();
            Set<String> missingPlayers = new HashSet<>();
            Set<String> toDelete = new HashSet<>();
            Map<String, ChannelPointPlayer> byTwitchId = new HashMap<String, ChannelPointPlayer>();
            for (ChannelPointPlayer player : plugin.players) {
                String twitchId = player.getTwitchId();
                if (twitchId == null) continue;
                missingPlayers.add(twitchId);
                byTwitchId.put(twitchId, player);
            }
            for (JsonObject subscription : eventSubSubscriptions) {
                String id = null;
                try {
                    id = subscription.get("id").getAsString();
                    String status = subscription.get("status").getAsString();
                    String twitchId = subscription.getAsJsonObject("condition").get("broadcaster_user_id").getAsString();
                    String callback = getString(subscription.getAsJsonObject("transport").get("callback"));
                    boolean validSubscription =
                            (status.equals("enabled") || status.equals("webhook_callback_verification_pending"))
                            && twitchNotificationEndpoint.equals(callback);
                    if (!validSubscription || !missingPlayers.remove(twitchId)) {
                        toDelete.add(id);
                    }
                } catch (Exception e) {
                    toDelete.add(id);
                }
            }
            for (String id : toDelete) {
                deleteSubscription(id);
            }
            for (String twitchId : missingPlayers) {
                ChannelPointPlayer player = byTwitchId.get(twitchId);
                if (player.getRefreshToken() == null) continue;
                try {
                    addSubscription(
                            twitchId,
                            "channel.channel_points_custom_reward_redemption.add",
                            "1"
                    );
                } catch (ForbiddenException e) {
                    player.invalidateRefreshToken();
                }
            }
        }));
    }

    public void completeRedemption(ChannelPointPlayer player, String rewardId, String redemptionId) {
        queueAction(new UserAction(player, (p) -> {
            markRedemptionAsComplete(p, rewardId, redemptionId);
        }));
    }

    private int markRedemptionAsComplete(ChannelPointPlayer player, String rewardId, String redemptionId) throws IOException, ForbiddenException, UnauthorizedException, TokenUnavailableException {
        return markRedemption(player, rewardId, redemptionId, "FULFILLED");
    }

    public void rejectRedemption(ChannelPointPlayer player, String rewardId, String redemptionId) {
        queueAction(new UserAction(player, (p) -> {
            markRedemptionAsRejected(p, rewardId, redemptionId);
        }));
    }

    private int markRedemptionAsRejected(ChannelPointPlayer player, String rewardId, String redemptionId) throws IOException, ForbiddenException, UnauthorizedException, TokenUnavailableException {
        return markRedemption(player, rewardId, redemptionId, "CANCELED");
    }

    private int markRedemption(ChannelPointPlayer player, String rewardId, String redemptionId, String status) throws IOException, ForbiddenException, UnauthorizedException, TokenUnavailableException {
        HttpURLConnection connection = connect(player, "https://api.twitch.tv/helix/channel_points/custom_rewards/redemptions?" +
                UrlUtils.urlEncodeMap(
                        new MapBuilder<String, String>()
                                .add("broadcaster_id", player.getTwitchId())
                                .add("id", redemptionId)
                                .add("reward_id", rewardId)
                                .toMap()
                ));
        JsonObject statusObject = new JsonObject();
        statusObject.addProperty("status", status);
        patch(connection, statusObject);
        return getResponseCode(connection);
    }
}

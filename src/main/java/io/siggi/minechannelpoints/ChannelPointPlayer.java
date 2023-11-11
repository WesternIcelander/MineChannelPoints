package io.siggi.minechannelpoints;

import io.siggi.minechannelpoints.twitchapi.TokenResponse;
import io.siggi.minechannelpoints.twitchapi.TokenUnavailableException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChannelPointPlayer {
    private final UUID minecraftUuid;
    private String twitchId;
    private String authToken;
    private String refreshToken;
    private final Map<String, ChannelPointReward> currentRewards = new HashMap<>();

    public ChannelPointPlayer(
            UUID minecraftUuid,
            String twitchId,
            String authToken,
            String refreshToken
    ) {
        this.minecraftUuid = minecraftUuid;
        this.twitchId = twitchId;
        this.authToken = authToken;
        this.refreshToken = refreshToken;
    }

    public Map<String, ChannelPointReward> getCurrentRewards() {
        return currentRewards;
    }

    public void invalidateRefreshToken() {
        refreshToken = null;
        authToken = null;
        MineChannelPoints.getInstance().save(this);
    }

    public void invalidateToken() {
        authToken = null;
        MineChannelPoints.getInstance().save(this);
    }

    public UUID getMinecraftUuid() {
        return minecraftUuid;
    }

    public String getTwitchId() {
        return twitchId;
    }

    public void setTwitchId(String twitchId) {
        this.twitchId = twitchId;
        MineChannelPoints.getInstance().save(this);
    }

    public void storeAuthTokenResponse(TokenResponse response) {
        this.authToken = response.accessToken();
        this.refreshToken = response.refreshToken();
        MineChannelPoints.getInstance().save(this);
    }

    public String directGetAuthToken() {
        return authToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAuthToken() throws TokenUnavailableException, IOException {
        if (authToken == null) {
            if (refreshToken == null) throw new TokenUnavailableException();
            try {
                TokenResponse response = MineChannelPoints.getTwitchApi().refreshAuthorization(refreshToken);
                storeAuthTokenResponse(response);
            } catch (TokenUnavailableException e) {
                refreshToken = null;
                throw e;
            }
        }
        return authToken;
    }
}

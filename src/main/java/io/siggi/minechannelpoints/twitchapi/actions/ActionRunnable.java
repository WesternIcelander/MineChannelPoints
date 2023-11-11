package io.siggi.minechannelpoints.twitchapi.actions;

import io.siggi.minechannelpoints.ChannelPointPlayer;
import io.siggi.minechannelpoints.twitchapi.ForbiddenException;
import io.siggi.minechannelpoints.twitchapi.TokenUnavailableException;
import io.siggi.minechannelpoints.twitchapi.UnauthorizedException;

import java.io.IOException;

@FunctionalInterface
public interface ActionRunnable {
    void run(ChannelPointPlayer player) throws ForbiddenException, UnauthorizedException, TokenUnavailableException, IOException;
}

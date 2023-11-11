package io.siggi.minechannelpoints.twitchapi.actions;

import io.siggi.minechannelpoints.ChannelPointPlayer;
import io.siggi.minechannelpoints.twitchapi.ForbiddenException;
import io.siggi.minechannelpoints.twitchapi.TokenUnavailableException;
import io.siggi.minechannelpoints.twitchapi.UnauthorizedException;

import java.io.IOException;

public final class UserAction extends Action {
    private final ChannelPointPlayer player;
    private final ActionRunnable runnable;

    public UserAction(ChannelPointPlayer player, ActionRunnable runnable) {
        this.player = player;
        this.runnable = runnable;
    }

    public final boolean run() {
        try {
            runnable.run(player);
            return true;
        } catch (IOException e) {
            return false;
        } catch (ForbiddenException e) {
            // we're not allowed to perform this action
            return true;
        } catch (UnauthorizedException e) {
            player.invalidateToken();
            return false;
        } catch (TokenUnavailableException e) {
            return true;
        }
    }
}

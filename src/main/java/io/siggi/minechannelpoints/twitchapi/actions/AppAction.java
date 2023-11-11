package io.siggi.minechannelpoints.twitchapi.actions;

import io.siggi.minechannelpoints.MineChannelPoints;
import io.siggi.minechannelpoints.twitchapi.ForbiddenException;
import io.siggi.minechannelpoints.twitchapi.TokenUnavailableException;
import io.siggi.minechannelpoints.twitchapi.UnauthorizedException;

import java.io.IOException;
import java.util.logging.Level;

public final class AppAction extends Action {
    private final ActionRunnable runnable;

    public AppAction(ActionRunnable runnable) {
        this.runnable = runnable;
    }

    public final boolean run() {
        try {
            runnable.run(null);
            return true;
        } catch (IOException e) {
            return false;
        } catch (ForbiddenException e) {
            // we're not allowed to perform this action
            return true;
        } catch (UnauthorizedException e) {
            MineChannelPoints.getTwitchApi().invalidateAppAccessToken();
            return false;
        } catch (TokenUnavailableException e) {
            return true;
        }
    }
}

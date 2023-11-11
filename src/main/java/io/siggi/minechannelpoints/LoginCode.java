package io.siggi.minechannelpoints;

import java.util.UUID;

public record LoginCode(String code, UUID minecraftUuid, long expiry) {
}

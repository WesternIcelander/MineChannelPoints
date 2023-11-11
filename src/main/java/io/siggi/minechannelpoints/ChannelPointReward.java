package io.siggi.minechannelpoints;

public record ChannelPointReward(String id, String name, String description, boolean stringInput, int minimumCost, ChannelPointAction action) {
}

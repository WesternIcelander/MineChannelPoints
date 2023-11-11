package io.siggi.minechannelpoints.util;

import java.util.HashMap;
import java.util.Map;

public final class MapBuilder<K,V> {
    private final Map<K,V> map = new HashMap<>();
    public MapBuilder<K,V> add(K key, V value) {
        map.put(key, value);
        return this;
    }
    public Map<K,V> toMap() {
        return map;
    }
}

package net.teamfruit.eewbot.registry;

import reactor.util.annotation.NonNull;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class MapRegistry<K, V> extends ConfigurationRegistry<Map<K, V>> implements Map<K, V> {

    public MapRegistry(Path path, Supplier<Map<K, V>> defaultElement, Type type) {
        super(path, defaultElement, type);
    }

    @Override
    public int size() {
        return getElement().size();
    }

    @Override
    public boolean isEmpty() {
        return getElement().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return getElement().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return getElement().containsValue(value);
    }

    @Override
    public V get(Object key) {
        return getElement().get(key);
    }

    @Override
    public V put(K key, V value) {
        return getElement().put(key, value);
    }

    @Override
    public V remove(Object key) {
        return getElement().remove(key);
    }

    @Override
    public void putAll(@NonNull Map<? extends K, ? extends V> m) {
        getElement().putAll(m);
    }

    @Override
    public void clear() {
        getElement().clear();
    }

    @Override
    public @NonNull Set<K> keySet() {
        return getElement().keySet();
    }

    @Override
    public @NonNull Collection<V> values() {
        return getElement().values();
    }

    @Override
    public @NonNull Set<Entry<K, V>> entrySet() {
        return getElement().entrySet();
    }
}

package net.teamfruit.eewbot.registry;

import net.teamfruit.eewbot.EEWBot;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class ConfigurationRegistry<E> {

    private E element;
    private final Supplier<E> supplier;
    private final Path path;
    private final Type type;

    public ConfigurationRegistry(final Path path, final Supplier<E> defaultElement, final Type type) {
        this.path = path;
        this.supplier = defaultElement;
        this.type = type;

    }

    public Path getPath() {
        return this.path;
    }

    public E getElement() {
        return this.element;
    }

    public ConfigurationRegistry<E> setElement(final E element) {
        this.element = element;
        return this;
    }

    public ConfigurationRegistry<E> init() throws IOException {
        if (!createIfNotExists()) {
            load();
            save();
        }
        return this;
    }

    private static void writeConfig(final Path path, final Object obj, final Type type) throws IOException {
        Files.createDirectories(path.getParent());
        try (Writer w = Files.newBufferedWriter(path)) {
            if (type != null)
                EEWBot.GSON.toJson(obj, type, w);
            else
                EEWBot.GSON.toJson(obj, w);
        }
    }

    private static <T> T readConfig(final Path path, final Class<T> clazz) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            return EEWBot.GSON.fromJson(r, clazz);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readConfig(final Path path, final Type type) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            return (T) EEWBot.GSON.fromJson(r, type);
        }
    }

    private boolean createIfNotExists() throws IOException {
        if (Files.notExists(this.path)) {
            writeConfig(this.path, this.element = this.supplier.get(), this.type);
            return true;
        }
        return false;
    }

    public void load() throws IOException {
        this.element = readConfig(this.path, this.type);
    }

    public void save() throws IOException {
        writeConfig(this.path, this.element, this.type);
    }
}

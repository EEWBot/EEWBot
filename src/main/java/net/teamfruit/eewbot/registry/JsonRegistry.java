package net.teamfruit.eewbot.registry;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public class JsonRegistry<E> {

    private E element;
    private final Supplier<E> supplier;
    private final Path path;
    private final Type type;
    private final Gson gson;

    public JsonRegistry(final Path path, final Supplier<E> defaultElement, final Type type, final Gson gson) {
        this.path = path;
        this.supplier = defaultElement;
        this.type = type;
        this.gson = gson;
    }

    public Path getPath() {
        return this.path;
    }

    public E getElement() {
        return this.element;
    }

    public void setElement(final E element) {
        this.element = element;
    }

    public JsonRegistry<E> init() throws IOException {
        if (!createIfNotExists()) {
            load();
            save();
        }
        return this;
    }

    private boolean createIfNotExists() throws IOException {
        if (Files.notExists(this.path)) {
            this.element = this.supplier.get();
            save();
            return true;
        }
        return false;
    }

    public void load() throws IOException {
        try (Reader r = Files.newBufferedReader(this.path)) {
            this.element = this.gson.fromJson(r, this.type);
        }
    }

    public void save() throws IOException {
        Path parent = this.path.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        try (Writer w = Files.newBufferedWriter(this.path)) {
            if (this.type != null)
                this.gson.toJson(this.element, this.type, w);
            else
                this.gson.toJson(this.element, w);
        }
    }
}

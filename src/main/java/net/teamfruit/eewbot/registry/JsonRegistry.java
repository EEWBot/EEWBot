package net.teamfruit.eewbot.registry;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    public void init(boolean strict) throws IOException {
        if (!createIfNotExists()) {
            load(strict);
            save();
        }
    }

    private boolean createIfNotExists() throws IOException {
        if (Files.notExists(this.path)) {
            this.element = this.supplier.get();
            save();
            return true;
        }
        return false;
    }

    public void load(boolean strict) throws IOException {
        String s = Files.readString(this.path);

        if (strict) {
            JsonObject jsonObj = JsonParser.parseString(s).getAsJsonObject();
            Set<String> jsonFields = new HashSet<>(jsonObj.keySet());
            Set<String> classFields = Arrays.stream(TypeToken.get(this.type).getRawType().getDeclaredFields())
                    .map(Field::getName)
                    .collect(Collectors.toSet());

            jsonFields.removeAll(classFields);
            if (!jsonFields.isEmpty()) {
                throw new JsonParseException("Unknown JSON fields: " + jsonFields);
            }
        }

        this.element = this.gson.fromJson(s, this.type);
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

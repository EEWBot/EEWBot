package net.teamfruit.eewbot.registry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.teamfruit.eewbot.Log;

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

    public E getElement() {
        return this.element;
    }

    public boolean exists() {
        return Files.exists(this.path);
    }

    public void init(boolean warnUnknownFields) throws IOException {
        if (!createIfNotExists()) {
            load(warnUnknownFields);
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

    /**
     * Returns the top level keys of the JSON file, or an empty set if the file
     * does not contain a JSON object. Used by callers to detect which schema
     * version a file was written with.
     */
    public Set<String> readTopLevelKeys() throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(this.path));
        if (!root.isJsonObject())
            return Set.of();
        return new HashSet<>(root.getAsJsonObject().keySet());
    }

    /**
     * Loads the file into {@link #getElement()}. Fields that are unknown to the
     * target type are ignored by Gson; when {@code warnUnknownFields} is set they
     * are additionally reported in the log. Unknown fields are never fatal, since
     * treating them as an error would risk overwriting a user's configuration on
     * the next {@link #save()}.
     */
    public void load(boolean warnUnknownFields) throws IOException {
        String s = Files.readString(this.path);

        if (warnUnknownFields) {
            JsonObject jsonObj = JsonParser.parseString(s).getAsJsonObject();
            Set<String> jsonFields = new HashSet<>(jsonObj.keySet());
            Set<String> classFields = Arrays.stream(TypeToken.get(this.type).getRawType().getDeclaredFields())
                    .map(Field::getName)
                    .collect(Collectors.toSet());

            jsonFields.removeAll(classFields);
            if (!jsonFields.isEmpty()) {
                Log.logger.warn("Unknown fields in {} will be dropped: {}", this.path, jsonFields);
            }
        }

        this.element = this.gson.fromJson(s, this.type);
        if (this.element == null)
            this.element = this.supplier.get();
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

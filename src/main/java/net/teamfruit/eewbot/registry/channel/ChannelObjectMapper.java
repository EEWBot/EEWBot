package net.teamfruit.eewbot.registry.channel;

import com.google.gson.Gson;
import redis.clients.jedis.json.JsonObjectMapper;

public class ChannelObjectMapper implements JsonObjectMapper {

    private final Gson gson;

    public ChannelObjectMapper(Gson gson) {
        this.gson = gson;
    }

    @Override
    public <T> T fromJson(String s, Class<T> aClass) {
        return this.gson.fromJson(s, aClass);
    }

    @Override
    public String toJson(Object o) {
        return this.gson.toJson(o);
    }
}

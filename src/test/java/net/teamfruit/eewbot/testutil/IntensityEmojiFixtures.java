package net.teamfruit.eewbot.testutil;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.ApplicationEmoji;
import discord4j.core.object.entity.ApplicationInfo;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import net.teamfruit.eewbot.registry.IntensityEmojiRegistry;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Map;

import static org.mockito.Mockito.*;

/** Fixed application emoji IDs shared by golden tests and their expected JSON generator. */
public final class IntensityEmojiFixtures {

    private static final Map<SeismicIntensity, Long> EMOJI_IDS = Map.of(
            SeismicIntensity.ONE, 1570523337678131352L,
            SeismicIntensity.TWO, 1570523341402415264L,
            SeismicIntensity.THREE, 1570523343570870363L,
            SeismicIntensity.FOUR, 1570523345907355658L,
            SeismicIntensity.FIVE_MINUS, 1570523348050378842L,
            SeismicIntensity.FIVE_PLUS, 1570523350353055816L,
            SeismicIntensity.SIX_MINUS, 1570523352655724544L,
            SeismicIntensity.SIX_PLUS, 1570523355084492913L,
            SeismicIntensity.SEVEN, 1570523357244555336L
    );

    private IntensityEmojiFixtures() {
    }

    public static IntensityEmojiRegistry registry() throws IOException {
        ApplicationInfo application = mock(ApplicationInfo.class);
        when(application.getEmojis()).thenReturn(Flux.fromIterable(EMOJI_IDS.entrySet())
                .map(entry -> {
                    ApplicationEmoji emoji = mock(ApplicationEmoji.class);
                    when(emoji.getName()).thenReturn("intensity_" + entry.getKey().getIconName().replace('-', '_'));
                    when(emoji.getId()).thenReturn(Snowflake.of(entry.getValue()));
                    return emoji;
                }));
        HttpClient httpClient = mock(HttpClient.class);
        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initialize(application, httpClient);
        verifyNoInteractions(httpClient);
        return registry;
    }
}

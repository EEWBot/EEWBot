package net.teamfruit.eewbot.registry;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.ApplicationEmoji;
import discord4j.core.object.entity.ApplicationInfo;
import discord4j.core.spec.ApplicationEmojiCreateSpec;
import discord4j.rest.util.Image;
import net.teamfruit.eewbot.entity.SeismicIntensity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IntensityEmojiRegistryTest {

    private static final List<String> ICONS = List.of("1", "2", "3", "4", "5-minus", "5-plus", "6-minus", "6-plus", "7");
    private static final List<SeismicIntensity> INTENSITIES = List.of(
            SeismicIntensity.ONE, SeismicIntensity.TWO, SeismicIntensity.THREE, SeismicIntensity.FOUR,
            SeismicIntensity.FIVE_MINUS, SeismicIntensity.FIVE_PLUS, SeismicIntensity.SIX_MINUS,
            SeismicIntensity.SIX_PLUS, SeismicIntensity.SEVEN);
    private final ApplicationInfo application = mock(ApplicationInfo.class);
    private final HttpClient httpClient = mock(HttpClient.class);

    @Test
    void existingNamesReuseIdsWithoutDownloadingOrCreatingAnything() throws Exception {
        List<ApplicationEmoji> existing = existingEmojis();
        when(this.application.getEmojis()).thenReturn(Flux.fromIterable(existing));

        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initialize(this.application, this.httpClient);

        for (int i = 0; i < INTENSITIES.size(); i++) {
            assertThat(registry.getId(INTENSITIES.get(i))).contains(Snowflake.of(100 + i));
            assertThat(registry.getImageUrl(INTENSITIES.get(i)))
                    .contains("https://cdn.discordapp.com/emojis/" + (100 + i) + ".webp");
            assertThat(registry.getMention(INTENSITIES.get(i)))
                    .contains("<:intensity_" + ICONS.get(i).replace('-', '_') + ":" + (100 + i) + ">");
        }
        assertThat(registry.getId(SeismicIntensity.UNKNOWN)).isEmpty();
        assertThat(registry.getImageUrl(SeismicIntensity.UNKNOWN)).isEmpty();
        assertThat(registry.getMention(SeismicIntensity.UNKNOWN)).isEmpty();
        verify(this.application, never()).createEmoji(any(ApplicationEmojiCreateSpec.class));
        verifyNoInteractions(this.httpClient);
    }

    @Test
    void missingEmojisAreCreatedFromTheCorrespondingPngsWithNormalizedNames() throws Exception {
        when(this.application.getEmojis()).thenReturn(Flux.empty());
        byte[] image = {1, 2, 3};
        stubDownload(200, image);
        AtomicLong nextId = new AtomicLong(200);
        when(this.application.createEmoji(any(ApplicationEmojiCreateSpec.class))).thenAnswer(call -> {
            ApplicationEmojiCreateSpec spec = call.getArgument(0);
            return Mono.just(emoji(spec.name(), nextId.getAndIncrement()));
        });

        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initialize(this.application, this.httpClient);

        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(this.httpClient, times(9)).send(requests.capture(), any());
        assertThat(requests.getAllValues()).extracting(request -> request.uri().toString())
                .containsExactlyElementsOf(ICONS.stream()
                        .map(icon -> "https://resources.eewbot.net/intensity-icon/" + icon + ".png").toList());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.timeout()).contains(Duration.ofSeconds(30));
        });
        ArgumentCaptor<ApplicationEmojiCreateSpec> creations = ArgumentCaptor.forClass(ApplicationEmojiCreateSpec.class);
        verify(this.application, times(9)).createEmoji(creations.capture());
        assertThat(creations.getAllValues()).extracting(ApplicationEmojiCreateSpec::name)
                .containsExactly("intensity_1", "intensity_2", "intensity_3", "intensity_4", "intensity_5_minus",
                        "intensity_5_plus", "intensity_6_minus", "intensity_6_plus", "intensity_7");
        assertThat(creations.getAllValues()).allSatisfy(spec -> {
            assertThat(spec.image().getFormat()).isEqualTo(Image.Format.PNG);
            assertThat(spec.image().getData()).isEqualTo(image);
        });
        for (int i = 0; i < INTENSITIES.size(); i++) {
            assertThat(registry.getId(INTENSITIES.get(i))).contains(Snowflake.of(200 + i));
            assertThat(registry.getImageUrl(INTENSITIES.get(i)))
                    .contains("https://cdn.discordapp.com/emojis/" + (200 + i) + ".webp");
        }
    }

    @Test
    void partialRegistrationCreatesOnlyTheMissingNameAndReusesItOnRestart() throws Exception {
        List<ApplicationEmoji> existing = existingEmojis();
        ApplicationEmoji unrelated = emoji("unrelated", 999);
        when(this.application.getEmojis()).thenReturn(Flux.concat(Flux.fromIterable(existing.subList(0, 8)), Flux.just(unrelated)));
        stubDownload(200, new byte[]{1});
        when(this.application.createEmoji(any(ApplicationEmojiCreateSpec.class))).thenReturn(Mono.just(existing.get(8)));

        IntensityEmojiRegistry first = IntensityEmojiRegistry.initialize(this.application, this.httpClient);
        when(this.application.getEmojis()).thenReturn(Flux.fromIterable(existing));
        IntensityEmojiRegistry second = IntensityEmojiRegistry.initialize(this.application, this.httpClient);

        verify(this.httpClient, times(1)).send(any(), any());
        verify(this.application, times(1)).createEmoji(any(ApplicationEmojiCreateSpec.class));
        for (SeismicIntensity intensity : INTENSITIES) {
            assertThat(second.getId(intensity)).isEqualTo(first.getId(intensity));
        }
    }

    @Test
    void listingFailureDoesNotCreateEmojis() {
        when(this.application.getEmojis()).thenReturn(Flux.error(new IllegalStateException("list failed")));

        assertThatThrownBy(() -> IntensityEmojiRegistry.initialize(this.application, this.httpClient))
                .isInstanceOf(IllegalStateException.class).hasMessage("list failed");

        verify(this.application, never()).createEmoji(any(ApplicationEmojiCreateSpec.class));
        verifyNoInteractions(this.httpClient);
    }

    @Test
    void failedDownloadDoesNotUploadAnErrorPageAndOmitsOnlyThatEmoji() throws Exception {
        when(this.application.getEmojis()).thenReturn(Flux.empty());
        stubDownload(404, new byte[0]);

        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initialize(this.application, this.httpClient);

        verify(this.httpClient, times(9)).send(any(), any());
        verify(this.application, never()).createEmoji(any(ApplicationEmojiCreateSpec.class));
        for (SeismicIntensity intensity : INTENSITIES) {
            assertThat(registry.getId(intensity)).isEmpty();
        }
    }

    @Test
    void creationFailureSkipsThatEmojiAndKeepsTheRest() throws Exception {
        List<ApplicationEmoji> existing = existingEmojis();
        when(this.application.getEmojis()).thenReturn(Flux.fromIterable(existing.subList(1, 9)));
        stubDownload(200, new byte[]{1});
        when(this.application.createEmoji(any(ApplicationEmojiCreateSpec.class)))
                .thenReturn(Mono.error(new IllegalStateException("create failed")));

        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initialize(this.application, this.httpClient);

        assertThat(registry.getId(SeismicIntensity.ONE)).isEmpty();
        for (int i = 1; i < INTENSITIES.size(); i++) {
            assertThat(registry.getId(INTENSITIES.get(i))).contains(Snowflake.of(100 + i));
        }
    }

    @Test
    void backgroundInitializationReturnsImmediatelyAndFillsLater() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        List<ApplicationEmoji> existing = existingEmojis();
        when(this.application.getEmojis()).thenReturn(Flux.defer(() -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Flux.fromIterable(existing);
        }));

        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initializeInBackground(this.application, this.httpClient);
        assertThat(registry.getId(SeismicIntensity.ONE)).isEmpty();

        release.countDown();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (registry.getId(SeismicIntensity.SEVEN).isEmpty() && System.nanoTime() < deadline) Thread.sleep(10);
        for (int i = 0; i < INTENSITIES.size(); i++) {
            assertThat(registry.getId(INTENSITIES.get(i))).contains(Snowflake.of(100 + i));
        }
    }

    @Test
    void backgroundListingFailureLeavesTheRegistryEmpty() throws Exception {
        when(this.application.getEmojis()).thenReturn(Flux.error(new IllegalStateException("list failed")));

        IntensityEmojiRegistry registry = IntensityEmojiRegistry.initializeInBackground(this.application, this.httpClient);

        verify(this.application, timeout(5000)).getEmojis();
        Thread.sleep(50);
        assertThat(registry.getId(SeismicIntensity.ONE)).isEmpty();
        verify(this.application, never()).createEmoji(any(ApplicationEmojiCreateSpec.class));
    }

    @Test
    void interruptedDownloadRestoresTheInterruptFlag() throws Exception {
        when(this.application.getEmojis()).thenReturn(Flux.empty());
        when(this.httpClient.send(any(), any())).thenThrow(new InterruptedException("interrupted"));
        try {
            assertThatThrownBy(() -> IntensityEmojiRegistry.initialize(this.application, this.httpClient))
                    .isInstanceOf(IOException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(this.application, never()).createEmoji(any(ApplicationEmojiCreateSpec.class));
        } finally {
            Thread.interrupted();
        }
    }

    private static List<ApplicationEmoji> existingEmojis() {
        return java.util.stream.IntStream.range(0, ICONS.size())
                .mapToObj(i -> emoji("intensity_" + ICONS.get(i).replace('-', '_'), 100 + i)).toList();
    }

    private static ApplicationEmoji emoji(String name, long id) {
        ApplicationEmoji emoji = mock(ApplicationEmoji.class);
        when(emoji.getName()).thenReturn(name);
        when(emoji.getId()).thenReturn(Snowflake.of(id));
        return emoji;
    }

    @SuppressWarnings("unchecked")
    private void stubDownload(int status, byte[] body) throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(this.httpClient.<byte[]>send(any(), any())).thenReturn(response);
    }
}

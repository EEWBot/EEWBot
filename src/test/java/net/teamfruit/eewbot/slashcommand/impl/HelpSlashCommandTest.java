package net.teamfruit.eewbot.slashcommand.impl;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.component.TopLevelMessageComponent;
import net.teamfruit.eewbot.i18n.I18n;
import net.teamfruit.eewbot.slashcommand.SlashCommandContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HelpSlashCommandTest {

    @ParameterizedTest
    @ValueSource(strings = {"ja_jp", "en_us", "zh_tw", "ko_hanja", "ko_kp"})
    void translatesDescriptionsAndLinksBeforeReplying(final String lang) {
        I18n i18n = new I18n("ja_jp");
        SlashCommandContext ctx = mock(SlashCommandContext.class);
        when(ctx.i18n()).thenReturn(i18n);
        ApplicationCommandInteractionEvent event = mock(ApplicationCommandInteractionEvent.class, RETURNS_DEEP_STUBS);

        new HelpSlashCommand().on(ctx, event, null, lang);

        ArgumentCaptor<List<TopLevelMessageComponent>> components = ArgumentCaptor.forClass(List.class);
        verify(event.reply()).withComponents(components.capture());
        String text = components.getValue().getFirst().getData().components().get().stream()
                .filter(component -> component.type() == 10)
                .map(component -> component.content().get())
                .collect(Collectors.joining("\n"));
        assertThat(text).contains(
                i18n.get(lang, "eewbot.scmd.help.field.set.value"),
                i18n.get(lang, "eewbot.scmd.help.field.links.value"),
                i18n.get(lang, "eewbot.scmd.help.field.legal.value"))
                .doesNotContain("eewbot.scmd.help.");
    }
}

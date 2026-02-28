package net.teamfruit.eewbot.registry.destination.delivery;

/**
 * 配信先を表す薄い型。配信パスに必要な情報のみ持つ。
 *
 * @param targetId   配信先ID
 * @param lang       言語コード
 * @param webhookUrl webhook URL（nullなら direct 送信）
 */
public record DeliveryTarget(
        long targetId,
        String lang,
        String webhookUrl // null なら direct
) {}

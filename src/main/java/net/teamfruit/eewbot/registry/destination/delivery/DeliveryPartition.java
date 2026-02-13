package net.teamfruit.eewbot.registry.destination.delivery;

import java.util.Map;

/**
 * webhook有無で分割された配信先。
 *
 * @param webhook webhook 付き配信先（targetId → DeliveryTarget）
 * @param direct  webhook 無し（direct送信）の配信先（targetId → DeliveryTarget）
 */
public record DeliveryPartition(
        Map<Long, DeliveryTarget> webhook,
        Map<Long, DeliveryTarget> direct
) {}

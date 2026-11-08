package com.emberstar.endbounember.network;

import java.util.List;

/**
 * 客户端缓存最近一次附魔台预览结果。
 * <p>只有容器 id 与当前打开的附魔台一致时才会被界面使用，避免残留旧数据。</p>
 */
public final class EnchantmentPreviewClientCache {
    private static volatile int containerId = -1;
    private static volatile List<List<EnchantmentPreviewPayload.Entry>> rows = List.of();

    private EnchantmentPreviewClientCache() {
    }

    public static void accept(EnchantmentPreviewPayload payload) {
        containerId = payload.containerId();
        rows = payload.rows();
    }

    /** 当前容器的三档预览；容器 id 不匹配时返回空列表 */
    public static List<List<EnchantmentPreviewPayload.Entry>> rowsFor(int targetContainerId) {
        return containerId == targetContainerId ? rows : List.of();
    }
}

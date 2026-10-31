package com.emberstar.endbounember.compat.ysm.mixin;

// 感谢酒石酸菌大佬的包容与支持，为了兼容这堆混淆代码，我的头发真的辛苦了。

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.emberstar.endbounember.compat.ysm.YsmSymbols;

/**
 * YSM 兼容 Mixin 的开关。
 * <p>
 * 只有安装了 YSM 才应用针对其混淆类的 Mixin；未安装时完全不加载，避免无谓的类加载与
 * 目标缺失警告。实体渲染上下文 Mixin 始终可用（它只依赖原版类）。
 * <p>
 * 探测方式：YSM 的 Mixin 应用发生在 Mixin 配置加载阶段，此时 FML 的 {@code ModList}
 * 尚未就绪（{@code ModList.get()} 返回 {@code null}），不能用它判断。这里改用类加载器
 * 直接探测 YSM 的入口类是否可见，该阶段类加载器已能解析模组 jar，结果可靠。
 * <p>
 * 参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的
 * {@code YsmMixinPlugin}，许可证全文见 THIRD-PARTY-NOTICES.md。
 */
public final class YsmMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("endbound_ember/ysm");

    /** YSM 的模组入口类，用于在 ModList 就绪前探测模组是否存在 */
    private static final String YSM_ENTRY_CLASS = "com.elfmcys.yesstevemodel.YesSteveModel";

    private boolean ysmLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        ysmLoaded = detectYsm();
        if (ysmLoaded) {
            LOGGER.info("[YSM Compat] 检测到 Yes Steve Model，启用光环头部锚点兼容（已验证版本 {}）",
                    YsmSymbols.VERIFIED_VERSION);
        } else {
            LOGGER.info("[YSM Compat] 未检测到 Yes Steve Model，光环使用原版头部锚点");
        }
    }

    /**
     * 通过类加载器探测 YSM 是否安装。
     * <p>
     * 只做 {@code Class.forName} 的存在性检查，不触发静态初始化，避免在 Mixin 阶段
     * 提前初始化 YSM 的类。
     */
    private static boolean detectYsm() {
        try {
            Class.forName(YSM_ENTRY_CLASS, false, YsmMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("YsmGeoRendererMixin")) {
            return ysmLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

package com.github.kdgaming0.enhancedstorage.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Conditional mixin application. Optional integrations (e.g. Skyblocker) are only mixed in
 * when the target mod is loaded, keeping the dependency soft.
 */
public final class EnhancedStorageMixinPlugin implements IMixinConfigPlugin {
    private static final String SKYBLOCKER_MOD_ID = "skyblocker";
    private static final String QUICK_NAV_BUTTON_MIXIN =
            "com.github.kdgaming0.enhancedstorage.mixin.QuickNavButtonMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (QUICK_NAV_BUTTON_MIXIN.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded(SKYBLOCKER_MOD_ID);
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

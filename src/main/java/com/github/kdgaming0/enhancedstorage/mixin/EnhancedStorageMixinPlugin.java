package com.github.kdgaming0.enhancedstorage.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnhancedStorageMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("enhanced_storage/mixin-plugin");

    /**
     * Method names each compat mixin injects into, keyed by simple mixin class name.
     * <p>
     * Mixin validates injection-handler descriptors during APPLY and throws — a hard crash during the *other*
     * mod's entrypoint, which is what RRV 8.7.0 caused when it changed {@code renderItemHighlighting}'s
     * signature. Handlers are written in the argument-less "simple" form wherever possible so parameter
     * changes can't break them, but a renamed or removed method still would. Checking the target's bytecode
     * before applying turns that into a quietly disabled compat feature instead.
     * <p>
     * Only list methods that are actually injection targets: those must be declared on the target class
     * itself, which is all a {@link ClassNode} exposes. Methods merely *called* from our code (e.g.
     * {@code SearchBar.isMouseOver}) may be inherited and must not be listed.
     */
    private static final Map<String, List<String>> REQUIRED_TARGET_METHODS = Map.of(
            "RRVItemViewOverlayMixin", List.of("renderItemHighlighting"),
            "RRVOverlayManagerMixin", List.of("renderAll"),
            "RRVOverlayManagerBackgroundMixin", List.of("renderAllBackground"),
            "RRVSearchBarMixin", List.of("mouseClicked"),
            "SkyblockerQuickNavButtonMixin", List.of("updateCoordinates"),
            "SkyHanniChestValueMixin", List.of("isValidStorage"),
            "SkyHanniInventoryUtilsMixin", List.of("getItemsInOpenChestWithNull", "inInventory"),
            "SkyHanniRenderDataMixin", List.of("onBackgroundDraw")
    );

    /**
     * Reads the target's bytecode (without loading or initialising the class) and confirms every method this
     * mixin injects into is still declared. Acts only on positive evidence: if the target can't be read at
     * all, the mixin is applied as before rather than silently dropped.
     */
    private static boolean hasRequiredMethods(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        List<String> required = REQUIRED_TARGET_METHODS.get(simpleName);
        if (required == null) return true;

        ClassNode target;
        try {
            target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Throwable t) {
            LOGGER.debug("Could not read {} to verify {}; applying it unchecked.", targetClassName, simpleName, t);
            return true;
        }

        for (String methodName : required) {
            boolean found = false;
            for (MethodNode method : target.methods) {
                if (method.name.equals(methodName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                LOGGER.warn("Skipping {}: {} no longer declares {}(). That compatibility feature is disabled; "
                        + "update Enhanced Storage if the other mod was recently updated.", simpleName, targetClassName, methodName);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return "";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.rrv.")) {
            return FabricLoader.getInstance().isModLoaded("rrv")
                    && hasRequiredMethods(targetClassName, mixinClassName);
        }
        if (mixinClassName.contains(".compat.skyblocker.")) {
            return FabricLoader.getInstance().isModLoaded("skyblocker")
                    && hasRequiredMethods(targetClassName, mixinClassName);
        }
        if (mixinClassName.contains(".compat.skyhanni.")) {
            return FabricLoader.getInstance().isModLoaded("skyhanni")
                    && hasRequiredMethods(targetClassName, mixinClassName);
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}

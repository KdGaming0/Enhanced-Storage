plugins {
    id("me.modmuss50.mod-publish-plugin") version "2.2.+" apply false
}

stonecutter active "26.1"

// See https://stonecutter.kikugie.dev/wiki/config/params
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"
    constants["release"] = property("mod.id") != "template"
    targets["fapi"] = node.project.property("deps.fabric_api") as String

    replacements.string(eval(node.metadata.version, ">=26.2")) {
        replace("mc.screen", "mc.gui.screen()")
        replace("mc.setScreen(", "mc.gui.setScreen(")
        replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
        replace("Minecraft.getInstance().setScreen(", "Minecraft.getInstance().gui.setScreen(")
    }

    replacements.string(eval(node.metadata.version, ">=26.3")) {
        replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
        replace("InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), ", "InputConstants.isKeyDown(")
    }
}

stonecutter tasks {
    // Upload versions one at a time, oldest first. `publishMods` only delegates to these, so ordering it does nothing.
    order("publishModrinth")
    order("publishCurseforge")
}

tasks.register("publishToAllPlatforms") {
    group       = "publishing"
    description = "Publish all release groups to Modrinth and CurseForge sequentially."
    dependsOn(stonecutter.tasks.names("publishMods").map { it.values })
}

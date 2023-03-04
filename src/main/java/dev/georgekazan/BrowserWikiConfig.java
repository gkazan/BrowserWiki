package dev.georgekazan;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BrowserWikiPlugin.CONFIG_GROUP_KEY)
public interface BrowserWikiConfig extends Config {

    @ConfigItem(
            keyName = "alwaysTop",
            name = "Window always on top",
            description = "If the browser window will always show over other windows"
    )
    default boolean alwaysOnTop() {
        return false;
    }

    @ConfigItem(
            keyName = "sizeX",
            name = "Window size X",
            description = "The browser window size in the x direction"
    )
    default int sizeX() {
        return 950;
    }


    @ConfigItem(
            keyName = "sizeY",
            name = "Window size Y",
            description = "What do you think?"
    )
    default int sizeY() {
        return 500;
    }
}

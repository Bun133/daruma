package com.bun133.daruma;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class Daruma extends JavaPlugin {
    public static GameManager manager;
    public static Logger logger;
    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();
        manager = new GameManager(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

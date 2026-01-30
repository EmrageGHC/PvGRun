package org.emrage.pvgrun.managers;

import org.bukkit. Difficulty;
import org.bukkit. configuration.file.FileConfiguration;
import org.emrage.pvgrun.Main;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {

    private final Main plugin;
    private FileConfiguration config;

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public int getBorderRadius() {
        return config.getInt("border-radius", 130);
    }

    public void setBorderRadius(int radius) {
        config.set("border-radius", radius);
        save();
    }

    public int getGameDurationSeconds() {
        return config.getInt("game-duration-seconds", 3600);
    }

    public void setGameDurationSeconds(int seconds) {
        config.set("game-duration-seconds", seconds);
        save();
    }

    public int getMobSpawnDelaySeconds() {
        return config.getInt("mob-spawn-delay-seconds", 600);
    }

    public void setMobSpawnDelaySeconds(int seconds) {
        config.set("mob-spawn-delay-seconds", seconds);
        save();
    }

    public boolean isUltraHardcore() {
        return config.getBoolean("ultra-hardcore", true);
    }

    public void setUltraHardcore(boolean enabled) {
        config.set("ultra-hardcore", enabled);
        save();
    }

    public Difficulty getDifficulty() {
        try {
            return Difficulty.valueOf(config.getString("difficulty", "HARD"));
        } catch (Exception e) {
            return Difficulty.HARD;
        }
    }

    public void setDifficulty(Difficulty difficulty) {
        config.set("difficulty", difficulty.name());
        save();
    }

    public List<String> getExcludedPlayers() {
        return config.getStringList("excluded-players");
    }

    public void setExcludedPlayers(List<String> players) {
        config.set("excluded-players", new ArrayList<>(players));
        save();
    }

    public void addExcludedPlayer(String name) {
        List<String> list = getExcludedPlayers();
        if (!list.contains(name)) {
            list.add(name);
            setExcludedPlayers(list);
        }
    }

    public void removeExcludedPlayer(String name) {
        List<String> list = getExcludedPlayers();
        list.remove(name);
        setExcludedPlayers(list);
    }

    public String getGameMode() {
        return config.getString("game-mode", "normal");
    }

    public void setGameMode(String mode) {
        config.set("game-mode", mode);
        save();
    }

    public boolean isNetherSpawnSet() {
        return config.getBoolean("nether-spawn-set", false);
    }

    public void setNetherSpawnSet(boolean set) {
        config.set("nether-spawn-set", set);
        save();
    }

    private void save() {
        plugin.saveConfig();
    }
}
package net.kalob.ResourceCasino;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PluginDescriptorTest {

    static YamlConfiguration load(String name) throws Exception {
        try (InputStream in = PluginDescriptorTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void mainClassExistsAndIsAPlugin() throws Exception {
        String main = load("plugin.yml").getString("main");
        assertEquals("net.kalob.ResourceCasino.ResourceCasinoPlugin", main);
        Class<?> cls = Class.forName(main, false, getClass().getClassLoader());
        assertTrue(JavaPlugin.class.isAssignableFrom(cls));
    }

    @Test
    void targetsMinecraft26_3() throws Exception {
        assertEquals("26.3", load("plugin.yml").getString("api-version"));
    }

    @Test
    void versionWasFilledInByTheBuild() throws Exception {
        String version = load("plugin.yml").getString("version");
        assertNotNull(version);
        assertFalse(version.contains("${"), "version placeholder was not expanded: " + version);
    }

    @Test
    void commandAndPermissionsAreDeclared() throws Exception {
        YamlConfiguration yml = load("plugin.yml");
        assertTrue(yml.isConfigurationSection("commands.casino"));
        assertEquals("op", yml.getString("permissions.resourcecasino.admin.default"));
        assertTrue(yml.getBoolean("permissions.resourcecasino.play.default"));
    }

    @Test
    void defaultConfigIsValid() throws Exception {
        YamlConfiguration config = load("config.yml");
        Wheel wheel = Wheel.of(config.getDoubleList("wheel.segments"));
        assertNotNull(wheel, "default wheel in config.yml is invalid");
        assertEquals(Wheel.DEFAULT_LAYOUT, wheel.layout());
        for (Resource r : Resource.values()) {
            assertTrue(config.getLong("max-bet." + r.configKey()) > 0, "no max bet for " + r);
        }
        double edge = config.getDouble("horse-racing.house-edge");
        assertTrue(edge > 0 && edge < 0.5);
        assertTrue(config.getBoolean("play-anywhere"));
    }
}

package scouting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class AppSettings {
    private static final Path SETTINGS_FILE =
            Path.of(System.getProperty("user.home"), ".scoutingapp", "settings.json");

    public String schemasFolder = "schemas";
    public String lastFormatName = "", lastCameraName = null;
    public boolean useQr = false;
    public Boolean mirrorPreview = false;

    // formatName -> last folder a CSV was saved to for that format (convenience only)
    public Map<String, String> lastFolderByFormat = new HashMap<>();

    public static AppSettings load() {
        try {
            if (Files.exists(SETTINGS_FILE)) {
                try (Reader reader = Files.newBufferedReader(SETTINGS_FILE)) {
                    return new Gson().fromJson(reader, AppSettings.class);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load settings, using defaults: " + e.getMessage());
        }
        return new AppSettings();
    }

    public void save() {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    public Path getSchemasFolder() { return Path.of(schemasFolder); }

    public Path getLastFolderFor(String formatName) {
        String path = lastFolderByFormat.get(formatName);
        return path == null ? null : Path.of(path);
    }

    public void setLastFolderFor(String formatName, Path folder) {
        lastFolderByFormat.put(formatName, folder.toAbsolutePath().toString());
        save();
    }
}
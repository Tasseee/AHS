package de.hugosniper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Laden/Speichern von config/hugo_sniper.json. Atomar speichern, beschaedigte Datei sichern. */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    public final List<String> warnings = new ArrayList<>();

    public ConfigStore(Path file) { this.file = file; }

    public Path path() { return file; }

    public HugoConfig load() {
        warnings.clear();
        if (!Files.exists(file)) {
            HugoConfig c = HugoConfig.defaults();
            save(c);
            return c;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            HugoConfig c = GSON.fromJson(json, HugoConfig.class);
            if (c == null) throw new JsonParseException("leere Datei");
            warnings.addAll(ConfigValidator.validate(c));
            return c;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            warnings.add("Config beschaedigt (" + e.getMessage() + ") - Sicherung angelegt, Standardwerte geladen");
            backupBroken();
            HugoConfig c = HugoConfig.defaults();
            save(c);
            return c;
        }
    }

    /** Atomar: erst temp-Datei schreiben, dann verschieben. Gibt false zurueck statt zu werfen. */
    public boolean save(HugoConfig c) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(c), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            warnings.add("Speichern fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    private void backupBroken() {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) { /* nichts weiter zu tun */ }
    }
}

package scouting.schema;

import com.google.gson.Gson;
import scouting.schema.Schema;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class SchemaManager {
    private final Path schemasFolder;
    private final Map<String, Schema> schemasByName = new LinkedHashMap<>();
    private final List<String> loadWarnings = new ArrayList<>();
    private final Gson gson = new Gson();

    public SchemaManager(Path schemasFolder) {
        this.schemasFolder = schemasFolder;
    }

    public void loadAll() throws IOException {
        schemasByName.clear();
        loadWarnings.clear();

        if (!Files.exists(schemasFolder)) {
            Files.createDirectories(schemasFolder);
        } else {
            try (Stream<Path> files = Files.list(schemasFolder)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        Schema schema = gson.fromJson(reader, Schema.class);

                        if (schema == null || schema.formatName == null || schema.columns == null) {
                            loadWarnings.add(file.getFileName() + ": missing required fields (formatName/columns) — skipped.");
                            continue;
                        }

                        int before = schema.columns.size();
                        schema.columns.removeIf(Objects::isNull);
                        int removed = before - schema.columns.size();
                        if (removed > 0) {
                            loadWarnings.add(file.getFileName() + ": " + removed
                                    + " malformed column entry" + (removed == 1 ? "" : "ies")
                                    + " (check for a stray comma) — those columns were skipped, rest of the format still loaded.");
                        }

                        schemasByName.put(schema.formatName, schema);
                    } catch (Exception e) {
                        loadWarnings.add(file.getFileName() + ": failed to load (" + e.getMessage() + ") — check the JSON syntax.");
                    }
                }
            }
        }

        if (schemasByName.isEmpty()) {
            loadWarnings.add("No format files found in the \"schemas\" folder ("
                    + schemasFolder.toAbsolutePath() + "). Add at least one .json file to collect data.");
        }
    }

    public List<String> getLoadWarnings() {
        return loadWarnings;
    }

    public List<String> availableFormatNames() {
        return new ArrayList<>(schemasByName.keySet());
    }

    public Schema get(String formatName) {
        return schemasByName.get(formatName);
    }
}
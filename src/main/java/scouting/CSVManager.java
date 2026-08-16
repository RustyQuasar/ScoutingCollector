package scouting;

import com.opencsv.CSVWriter;
import scouting.schema.Schema;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CSVManager {
    private final Path outputFolder;

    public CSVManager(Path outputFolder) {
        this.outputFolder = outputFolder;
    }

    public void appendRow(Schema schema, Map<String, String> rowValues) throws IOException {
        Files.createDirectories(outputFolder);
        Path file = outputFolder.resolve(sanitize(schema.formatName) + ".csv");
        boolean isNewFile = !Files.exists(file);

        try (CSVWriter writer = new CSVWriter(new FileWriter(file.toFile(), true))) {
            if (isNewFile) {
                writer.writeNext(schema.columns.stream().map(c -> c.label).toArray(String[]::new));
            }
            String[] row = schema.columnKeys().stream()
                    .map(key -> rowValues.getOrDefault(key, ""))
                    .toArray(String[]::new);
            writer.writeNext(row);
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
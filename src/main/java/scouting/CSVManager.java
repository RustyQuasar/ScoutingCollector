package scouting;

import com.opencsv.CSVWriter;
import scouting.schema.Schema;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class CSVManager {

    public void appendRow(Path file, Schema schema, Map<String, String> rowValues) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
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
}
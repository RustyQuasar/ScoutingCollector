package scouting.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScoutingRow {
    private final Map<String, StringProperty> values = new LinkedHashMap<>();

    // build an empty row shaped by the schema's column keys
    public ScoutingRow(List<String> columnKeys) {
        for (String key : columnKeys) {
            values.put(key, new SimpleStringProperty(""));
        }
    }

    public void set(String key, String value) {
        values.get(key).set(value);
    }

    public StringProperty property(String key) {
        return values.get(key);
    }

    public String[] toCsvRow(List<String> columnKeys) {
        return columnKeys.stream().map(k -> values.get(k).get()).toArray(String[]::new);
    }
}
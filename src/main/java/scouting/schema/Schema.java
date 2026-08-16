package scouting.schema;

import java.util.List;

public class Schema {
    public String formatName;
    public List<Column> columns;

    public static class Column {
        public String key;
        public String label;
        public Boolean showInTable;

        public boolean isVisible(){
            return showInTable == null || showInTable;
        }
    }

    public List<String> columnKeys() {
        return columns.stream().map(c -> c.key).toList();
    }
}
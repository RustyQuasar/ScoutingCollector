package scouting.schema;

import scouting.schema.SchemaManager;

import java.nio.file.Path;

public class SchemaManagerTest {
    //You run this only when adding a new schematic, just to make sure the json file can atleast be found
    public static void main(String[] args) throws Exception {
        SchemaManager manager = new SchemaManager(Path.of("schemas"));
        manager.loadAll();

        System.out.println("Loaded formats:");
        manager.availableFormatNames().forEach(System.out::println);
    }
}
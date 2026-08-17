module scouting {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    requires com.google.gson;
    requires com.opencsv;
    requires com.fazecast.jSerialComm;
    requires webcam.capture;

    opens scouting to javafx.fxml, com.google.gson;
    opens scouting.model to javafx.base;
    opens scouting.schema to com.google.gson;

    exports scouting;
}
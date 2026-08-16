package scouting;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import scouting.schema.SchemaManager;

public class Main extends Application {
    public static void main(String[] args) {
        launch(args); // hands control to JavaFX; JavaFX will call start() below
    }

    @Override
    public void start(Stage stage) throws Exception {
        AppSettings settings = AppSettings.load();

        SchemaManager schemaManager = new SchemaManager(settings.getSchemasFolder());
        schemaManager.loadAll();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();
        controller.init(schemaManager, settings); // manual dependency injection

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.setTitle("FRC Scouting Collector");

        // ensure collector services stop cleanly if the window is closed mid-run
        stage.setOnCloseRequest(e -> controller.shutdown());

        stage.show();
    }
}
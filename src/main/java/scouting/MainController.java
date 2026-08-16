package scouting;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import scouting.collectors.BluetoothCollectorService;
import scouting.collectors.QrCollectorService;
import scouting.model.ScoutingRow;
import scouting.schema.Schema;
import scouting.schema.SchemaManager;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MainController {
    private SchemaManager schemaManager;
    private AppSettings settings;
    private CSVManager csvManager;
    private QrCollectorService QRCollector;
    private BluetoothCollectorService BTCollector;

    @FXML
    private ComboBox<String> formatDropdown, cameraDropdown;
    @FXML
    private Label counterLabel, statusLabel;
    @FXML
    private Button startStopButton, refreshCamerasButton, toggleModes;

    @FXML
    private TableView<ScoutingRow> entryTable;
    @FXML
    private Label csvFileLabel;
    @FXML
    private ImageView cameraPreview;
    @FXML
    private CheckBox mirrorToggle;
    @FXML
    private StackPane previewArea;

    @FXML
    private ListView<String> btStatusList;
    @FXML private javafx.scene.layout.HBox qrControlsBox;

    private Path currentRunFile;
    private int collectedCount = 0;
    private boolean running = false;

    @FXML
    public void initialize() {
        startStopButton.setOnAction(e -> toggleServer());
        refreshCamerasButton.setOnAction(e -> refreshCameraList());
        toggleModes.setOnAction(e -> {
            swapModes();
            refreshModeUI();
        });
        mirrorToggle.setOnAction(e -> {
            boolean mirrored = mirrorToggle.isSelected();
            cameraPreview.setScaleX(mirrored ? -1 : 1);
            settings.mirrorPreview = mirrored;
            settings.save();
        });
    }

    public void init(SchemaManager schemaManager, AppSettings settings) {
        this.schemaManager = schemaManager;
        this.settings = settings;

        showSchemaWarningsIfAny();

        formatDropdown.getItems().setAll(schemaManager.availableFormatNames());

        if (!settings.lastFormatName.isBlank()
                && formatDropdown.getItems().contains(settings.lastFormatName)) {
            formatDropdown.setValue(settings.lastFormatName);
        } else if (!formatDropdown.getItems().isEmpty()) {
            formatDropdown.setValue(formatDropdown.getItems().get(0)); // fallback if no saved format yet
        }

        // setValue() above does NOT trigger setOnAction — must build columns manually here
        if (formatDropdown.getValue() != null) {
            rebuildTableForSchema(schemaManager.get(formatDropdown.getValue()));
        }

        mirrorToggle.setSelected(settings.mirrorPreview);
        cameraPreview.setScaleX(settings.mirrorPreview ? -1 : 1);

        mirrorToggle.setOnAction(e -> {
            boolean mirrored = mirrorToggle.isSelected();
            cameraPreview.setScaleX(mirrored ? -1 : 1);
            settings.mirrorPreview = mirrored;
            settings.save();
        });

        refreshCameraList();
        cameraDropdown.setOnAction(e -> {
            settings.lastCameraName = cameraDropdown.getValue();
            settings.save();
        });

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(previewArea.widthProperty());
        clip.heightProperty().bind(previewArea.heightProperty());
        previewArea.setClip(clip);

        refreshModeUI();

        formatDropdown.setOnAction(e -> {
            String selected = formatDropdown.getValue();
            settings.lastFormatName = selected;
            settings.save();
            rebuildTableForSchema(schemaManager.get(formatDropdown.getValue()));
        });
    }

    private void toggleServer() {
        if (!running) {
            Path chosen = promptForRunFile();
            if (chosen == null) return; // user cancelled — don't start anything

            currentRunFile = chosen;
            csvManager = new CSVManager(currentRunFile.getParent());

            Schema schema = schemaManager.get(formatDropdown.getValue());
            entryTable.getItems().clear();
            loadExistingRows(currentRunFile, schema);

            boolean started;
            collectedCount = 0;
            updateCounter(); // resets to "Collected: 0" or gets overwritten below for BT

            if (settings.useQr) {
                QRCollector = new QrCollectorService(schema.columnKeys(), cameraDropdown.getValue());
                QRCollector.setFrameHandler(cameraPreview::setImage);
                QRCollector.setErrorHandler(this::showCameraError);
                QRCollector.setStatusHandler(statusLabel::setText);
                started = QRCollector.start(this::onDataCollected);
            } else {
                BTCollector = new BluetoothCollectorService(schema.columnKeys());
                BTCollector.setPortStatusHandler(statuses -> btStatusList.getItems().setAll(statuses));
                BTCollector.setConnectionCountHandler(count ->
                        counterLabel.setText("Tablets connected: " + count));
                started = BTCollector.start(this::onDataCollected);
                statusLabel.setText("Mac Address: " + BTCollector.getLocalBluetoothMac());
            }

            if (!started) {
                currentRunFile = null;
                csvManager = null;
                return;
            }

            running = true;
            startStopButton.setText("Stop");
            toggleModes.setDisable(true); // lock mode during a run, per your correction
            csvFileLabel.setText("Recording to: " + currentRunFile.getFileName());
        } else {
            statusLabel.setText("Stopped");
            running = false;
            cameraPreview.setImage(null);
            btStatusList.getItems().clear();
            startStopButton.setText("Start");
            toggleModes.setDisable(false); // unlock now that we've stopped
            if (QRCollector != null) QRCollector.stop();
            if (BTCollector != null) BTCollector.stop();
            statusLabel.setText("Stopped");
            csvFileLabel.setText("No file selected");
            currentRunFile = null;
            collectedCount = 0;
            updateCounter();
        }
    }

    public void swapModes() {
        settings.useQr = !settings.useQr;
        settings.save();
        if (!settings.useQr) {
            toggleModes.setText("Using: Bluetooth");
        } else {
            toggleModes.setText("Using: Qr Scanning");
        }
    }

    private Path promptForRunFile() {
        String formatName = formatDropdown.getValue();
        if (formatName == null) return null;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save CSV for this run (" + formatName + ")");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Path lastFolder = settings.getLastFolderFor(formatName);
        if (lastFolder != null && Files.exists(lastFolder)) {
            chooser.setInitialDirectory(lastFolder.toFile());
        }
        // suggested name is just a starting point — user can rename to "Day 1 Morning" etc.
        chooser.setInitialFileName(formatName.replaceAll("[^a-zA-Z0-9-_]", "_") + ".csv");

        File chosen = chooser.showSaveDialog(startStopButton.getScene().getWindow());
        if (chosen == null) return null;

        settings.setLastFolderFor(formatName, chosen.toPath().getParent());
        return chosen.toPath();
    }

    private void onDataCollected(Map<String, String> rowValues) {
        if (currentRunFile == null) return; // safety guard — shouldn't happen, but don't write nowhere

        Schema schema = schemaManager.get(formatDropdown.getValue());

        ScoutingRow row = new ScoutingRow(schema.columnKeys());
        rowValues.forEach(row::set);
        entryTable.getItems().add(row);
        try {
            csvManager.appendRow(schema, rowValues);
        } catch (IOException e) {
            System.err.println("Failed to write row: " + e.getMessage());
        }

        collectedCount++;
        updateCounter();
    }

    private void updateCounter() {
        counterLabel.setText("Collected: " + collectedCount);
    }

    private void refreshCameraList() {
        String previouslySelected = cameraDropdown.getValue();
        cameraDropdown.getItems().setAll(QrCollectorService.listAvailableCameras());

        String toSelect = previouslySelected != null && cameraDropdown.getItems().contains(previouslySelected)
                ? previouslySelected
                : settings.lastCameraName;

        if (toSelect != null && cameraDropdown.getItems().contains(toSelect)) {
            cameraDropdown.setValue(toSelect);
        } else if (!cameraDropdown.getItems().isEmpty()) {
            cameraDropdown.setValue(cameraDropdown.getItems().get(0));
        }
    }

    private void rebuildTableForSchema(Schema schema) {
        entryTable.getColumns().clear();
        entryTable.getItems().clear();

        for (Schema.Column col : schema.columns) {
            if (!col.isVisible()) continue;

            TableColumn<ScoutingRow, String> column = new TableColumn<>(col.label);
            column.setCellValueFactory(data -> data.getValue().property(col.key));
            entryTable.getColumns().add(column);
        }
    }

    private void loadExistingRows(Path file, Schema schema) {
        if (!Files.exists(file)) return; // brand-new file — nothing to load, that's fine

        try (CSVReader reader = new CSVReader(new FileReader(file.toFile()))) {
            List<String[]> allRows = reader.readAll();
            if (allRows.size() <= 1) return; // header only, or empty file

            List<String> keys = schema.columnKeys();
            for (int i = 1; i < allRows.size(); i++) { // skip header row (index 0)
                String[] values = allRows.get(i);
                if (values.length != keys.size()) {
                    System.err.println("Skipping malformed CSV line " + (i + 1)
                            + ": expected " + keys.size() + " fields, got " + values.length);
                    continue;
                }
                ScoutingRow row = new ScoutingRow(keys);
                for (int c = 0; c < keys.size(); c++) {
                    row.set(keys.get(c), values[c]);
                }
                entryTable.getItems().add(row);
            }
        } catch (IOException | CsvException e) {
            System.err.println("Failed to load existing CSV data: " + e.getMessage());
        }
    }

    private void refreshModeUI() {
        boolean qr = settings.useQr;
        cameraPreview.setVisible(qr);
        cameraPreview.setManaged(qr);
        btStatusList.setVisible(!qr);
        btStatusList.setManaged(!qr);
        qrControlsBox.setVisible(qr);
        qrControlsBox.setManaged(qr);
    }

    private void showCameraError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle("Camera Error");
        alert.showAndWait();
    }

    private void showSchemaWarningsIfAny() {
        List<String> warnings = schemaManager.getLoadWarnings();
        if (warnings.isEmpty()) return;

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Schema File Issues");
        alert.setHeaderText("One or more format files (.json) have formatting problems:");
        alert.setContentText(String.join("\n\n", warnings));
        alert.showAndWait();
    }

    public void shutdown() {
        // stop QR camera / Bluetooth listeners here if running,
        // so the app doesn't leave ports open on exit
    }
}
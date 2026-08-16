package scouting.collectors;

import javafx.application.Platform;

import java.util.*;
import java.util.function.Consumer;

public class MockCollectorService implements CollectorService {
    private final List<String> columnKeys;
    private Timer timer;
    private boolean running = false;

    public MockCollectorService(List<String> columnKeys) {
        this.columnKeys = columnKeys;
    }

    @Override
    public boolean start(Consumer<Map<String, String>> onRowReceived) {
        running = true;
        timer = new Timer(true); // daemon thread — won't block JVM shutdown
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Map<String, String> fakeRow = new LinkedHashMap<>();

                for (String key : columnKeys) {
                    if (key.toLowerCase().contains("name")) {
                        fakeRow.put(key, "Declan");
                    } else {
                        fakeRow.put(key, key);
                    }
                }

                // Timer runs on a background thread — UI/callback code must
                // hop back onto the JavaFX Application Thread before touching UI
                Platform.runLater(() -> onRowReceived.accept(fakeRow));
            }
        }, 2000, 3000); // first row after 2s, then every 3s
        return false;
    }

    @Override
    public void stop() {
        running = false;
        if (timer != null) timer.cancel();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
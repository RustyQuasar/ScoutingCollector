package scouting.collectors;

import java.util.Map;
import java.util.function.Consumer;

public interface CollectorService {
    boolean start(Consumer<Map<String, String>> onRowReceived);
    void stop();
    boolean isRunning();
}
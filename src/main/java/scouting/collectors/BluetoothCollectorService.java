package scouting.collectors;

import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class BluetoothCollectorService implements CollectorService {
    private final List<String> columnKeys;
    private volatile boolean running = false;
    private final Map<String, PortListener> activeListeners = new ConcurrentHashMap<>();

    private Consumer<List<String>> portStatusHandler;   // live per-port status lines
    private Consumer<Integer> connectionCountHandler;    // cumulative, never decrements

    private int totalConnections = 0;

    public BluetoothCollectorService(List<String> columnKeys) {
        this.columnKeys = columnKeys;
    }

    public void setPortStatusHandler(Consumer<List<String>> handler) { this.portStatusHandler = handler; }
    public void setConnectionCountHandler(Consumer<Integer> handler) { this.connectionCountHandler = handler; }

    public String getLocalBluetoothMac() {
        try {
            Process process = new ProcessBuilder("getmac", "/fo", "csv", "/v")
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("bluetooth")) {
                        // CSV format: "Connection Name","Network Adapter","Physical Address","Transport Name"
                        String[] fields = line.split("\",\"");
                        if (fields.length >= 3) {
                            return fields[2].replace("\"", "").trim(); // the MAC address field
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not determine local Bluetooth MAC: " + e.getMessage());
        }
        return "Unavailable";
    }

    @Override
    public boolean start(Consumer<Map<String, String>> onRowReceived) {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) return false;

        running = true;
        totalConnections = 0;
        for (SerialPort port : ports) {
            PortListener listener = new PortListener(port, onRowReceived);
            activeListeners.put(port.getSystemPortName(), listener);
            listener.start();
        }
        notifyPortStatuses();
        return true;
    }

    @Override
    public void stop() {
        running = false;
        for (PortListener l : activeListeners.values()) l.stopListening();
        activeListeners.clear();
        notifyPortStatuses();
    }

    @Override
    public boolean isRunning() { return running; }

    private void notifyPortStatuses() {
        if (portStatusHandler == null) return;
        List<String> statuses = new ArrayList<>();
        for (PortListener l : activeListeners.values()) {
            statuses.add(l.getLabel() + ": " + (l.isConnected() ? "Tablet connected" : "Listening"));
        }
        Platform.runLater(() -> portStatusHandler.accept(statuses));
    }

    private void onNewConnection() {
        totalConnections++;
        int count = totalConnections;
        if (connectionCountHandler != null) {
            Platform.runLater(() -> connectionCountHandler.accept(count));
        }
    }

    private class PortListener {
        private final SerialPort port;
        private final Consumer<Map<String, String>> onRowReceived;
        private volatile boolean listening = true;
        private volatile boolean connected = false; // true once first byte arrives

        PortListener(SerialPort port, Consumer<Map<String, String>> onRowReceived) {
            this.port = port;
            this.onRowReceived = onRowReceived;
        }

        boolean isConnected() { return connected; }
        String getLabel() { return port.getSystemPortName(); }

        void start() {
            Thread thread = new Thread(this::run, "bt-" + port.getSystemPortName());
            thread.setDaemon(true);
            thread.start();
        }

        void stopListening() {
            listening = false;
            if (port.isOpen()) port.closePort();
        }

        private void run() {
            if (!port.openPort()) return; // nothing paired/available on this port — skip silently

            StringBuilder buffer = new StringBuilder();
            try {
                while (listening && running) {
                    int available = port.bytesAvailable();
                    if (available <= 0) {
                        Thread.sleep(100);
                        continue;
                    }

                    if (!connected) {
                        connected = true; // first bytes = our proxy for "a tablet is actually here"
                        onNewConnection();
                        notifyPortStatuses();
                    }

                    byte[] chunk = new byte[available];
                    int read = port.readBytes(chunk, chunk.length);
                    if (read <= 0) continue;

                    buffer.append(new String(chunk, 0, read, StandardCharsets.US_ASCII));

                    int nl;
                    while ((nl = buffer.indexOf("\n")) != -1) {
                        String line = buffer.substring(0, nl).trim();
                        buffer.delete(0, nl + 1);

                        if (line.equalsIgnoreCase("end")) {
                            listening = false;
                            break;
                        }
                        if (!line.isEmpty()) handleLine(line);
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                if (port.isOpen()) port.closePort();
                connected = false;
                notifyPortStatuses(); // reflects back to "Listening" once the tablet disconnects
            }
        }

        private void handleLine(String line) {
            // same convention as the QR sender: a trailing comma separates concatenated
            // files, but the very last one is a dangling artifact with nothing after it
            if (line.endsWith(",")) {
                line = line.substring(0, line.length() - 1);
            }

            String[] parts = line.split(",", -1);
            if (parts.length != columnKeys.size()) {
                System.err.println("Bluetooth row from " + getLabel() + " has " + parts.length
                        + " fields, expected " + columnKeys.size() + ". Ignoring.");
                return;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < columnKeys.size(); i++) {
                row.put(columnKeys.get(i), parts[i]);
            }
            Platform.runLater(() -> onRowReceived.accept(row));
        }
    }
}
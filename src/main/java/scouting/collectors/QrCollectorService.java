package scouting.collectors;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;

public class QrCollectorService implements CollectorService {
    private final List<String> columnKeys;
    private final String cameraName; // null = use system default
    private Webcam webcam;
    private Thread captureThread;
    private volatile boolean running = false;
    private String lastDecodedText = null;
    private int consecutiveFailures = 0;
    private static final int FAILURE_THRESHOLD = 8;
    private Consumer<Image> frameHandler;
    private Consumer<String> errorHandler;
    private Consumer<String> statusHandler;

    public QrCollectorService(List<String> columnKeys, String cameraName) {
        this.columnKeys = columnKeys;
        this.cameraName = cameraName;
    }

    public void setFrameHandler(Consumer<Image> frameHandler) {
        this.frameHandler = frameHandler;
    }

    public void setErrorHandler(Consumer<String> errorHandler) {
        this.errorHandler = errorHandler;
    }

    // list available camera names for the UI dropdown — call this BEFORE start()
    public static List<String> listAvailableCameras() {
        List<String> names = new ArrayList<>();
        for (Webcam cam : Webcam.getWebcams()) {
            names.add(cam.getName());
        }
        return names;
    }

    @Override
    public boolean start(Consumer<Map<String, String>> onRowReceived) {
        webcam = resolveWebcam();
        if (webcam == null) {
            notifyError("No camera found matching: " + cameraName);
            return false;
        }

        try {
            webcam.open();
        } catch (Exception e) {
            // most common cause: camera is locked by another application
            notifyError("Could not open camera \"" + webcam.getName()
                    + "\" — it may be in use by another app.");
            return false;
        }

        running = true;
        captureThread = new Thread(() -> runCaptureLoop(onRowReceived), "qr-capture-thread");
        captureThread.setDaemon(true);
        captureThread.start();
        return true;
    }

    private Webcam resolveWebcam() {
        if (cameraName == null) return Webcam.getDefault();
        for (Webcam cam : Webcam.getWebcams()) {
            if (cam.getName().equals(cameraName)) return cam;
        }
        return null; // previously-selected camera no longer connected
    }

    private void runCaptureLoop(Consumer<Map<String, String>> onRowReceived) {
        MultiFormatReader reader = new MultiFormatReader();
        try {
            while (running) {
                BufferedImage image;
                try {
                    image = webcam.getImage();
                } catch (Exception e) {
                    notifyError("Camera disconnected: " + e.getMessage());
                    running = false;
                    break;
                }
                if (image == null) continue;

                if (frameHandler != null) {
                    Image fxImage = SwingFXUtils.toFXImage(image, null);
                    Platform.runLater(() -> frameHandler.accept(fxImage));
                }

                try {
                    LuminanceSource source = new BufferedImageLuminanceSource(image);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                    Result result = reader.decode(bitmap);
                    String text = result.getText();
                    consecutiveFailures = 0;
                    setStatus("QR detected");

                    if (!text.equals(lastDecodedText)) {
                        lastDecodedText = text;
                        List<Map<String, String>> rows = parseCsvLine(text);
                        if (!rows.isEmpty()) {
                            Platform.runLater(() -> {
                                for (Map<String, String> row : rows) {
                                    onRowReceived.accept(row);
                                }
                                setStatus("Row collected ✓ (" + rows.size() + (rows.size() == 1 ? " row" : " rows") + ")");                            });
                        }
                    }
                } catch (ReaderException e) {
                    // catches NotFoundException, ChecksumException, FormatException alike —
                    // any of these means "no clean read this frame," never a crash
                    if (consecutiveFailures >= FAILURE_THRESHOLD) {
                        lastDecodedText = null;
                        setStatus("Searching for QR code...");
                    }
                } finally {
                    reader.reset(); // clear internal state between attempts, avoids stale partial reads
                }
            }
        } finally {
            if (webcam != null && webcam.isOpen()) webcam.close();
        }
    }

    private void setStatus(String status) {
        if (statusHandler != null) {
            Platform.runLater(() -> statusHandler.accept(status));
        }
    }

    private List<Map<String, String>> parseCsvLine(String line) {
        // each file's own trailing comma acts as the separator before the next file —
        // only the very last one is a dangling artifact with nothing after it
        if (line.endsWith(",")) {
            line = line.substring(0, line.length() - 1);
        }

        String[] parts = line.split(",", -1);

        int columnsPerRow = columnKeys.size();
        if (parts.length % columnsPerRow != 0) {
            System.err.println("QR data (" + parts.length
                    + " fields) doesn't divide evenly into " + columnsPerRow + "-column rows. Ignoring scan.");
            return Collections.emptyList();
        }

        int rowCount = parts.length / columnsPerRow;
        List<Map<String, String>> rows = new ArrayList<>();

        for (int r = 0; r < rowCount; r++) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < columnsPerRow; c++) {
                row.put(columnKeys.get(c), parts[r * columnsPerRow + c]);
            }
            rows.add(row);
        }
        return rows;
    }

    private void notifyError(String message) {
        System.err.println(message);
        if (errorHandler != null) {
            Platform.runLater(() -> errorHandler.accept(message));
        }
    }

    public void setStatusHandler(Consumer<String> statusHandler) {
        this.statusHandler = statusHandler;
    }

    @Override
    public void stop() {
        running = false;

        // don't block the caller (FX thread) — closing may hang on some drivers
        new Thread(() -> {
            if (captureThread != null) {
                try {
                    captureThread.join(1000); // give it 1s to exit cleanly on its own
                } catch (InterruptedException ignored) {}
            }
            // whether or not the loop exited cleanly, force the camera closed —
            // this often unblocks a hung getImage() call as a side effect
            if (webcam != null && webcam.isOpen()) {
                try {
                    webcam.close();
                } catch (Exception e) {
                    System.err.println("Error force-closing webcam: " + e.getMessage());
                }
            }
        }, "qr-stop-watchdog").start();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
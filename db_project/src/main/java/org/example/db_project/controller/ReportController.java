package org.example.db_project.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import org.example.db_project.model.ReportResult;
import org.example.db_project.model.ReportType;
import org.example.db_project.service.ReportService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReportController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private ComboBox<ReportType> reportCombo;
    @FXML private Label reportDescriptionLabel;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Spinner<Integer> limitSpinner;
    @FXML private Spinner<Integer> minOrdersSpinner;
    @FXML private TableView<List<Object>> reportTable;
    @FXML private Label statusLabel;

    private final ReportService reportService = new ReportService();
    private ReportResult lastResult;

    @FXML
    public void initialize() {
        reportCombo.setItems(FXCollections.observableArrayList(ReportType.values()));
        reportCombo.setValue(ReportType.BEST_SELLERS);
        reportCombo.valueProperty().addListener((obs, oldType, newType) -> {
            updateControls();
            handleRunReport();
        });
        reportTable.setPlaceholder(new Label("No data for this report."));
        reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        updateControls();
        handleRunReport();
    }

    /** Called by MainController whenever the Reports tab is opened, so figures are current. */
    public void refresh() {
        handleRunReport();
    }

    @FXML
    private void handleRunReport() {
        ReportType type = reportCombo.getValue();
        try {
            ReportResult result = reportService.run(type,
                    fromDatePicker.getValue(), toDatePicker.getValue(),
                    limitSpinner.getValue(), minOrdersSpinner.getValue());
            showResult(result);
            showStatus(String.format("%d row(s), query took %.2f ms.", result.rows().size(), result.elapsedMillis()), false);
        } catch (IllegalArgumentException e) {
            showStatus(e.getMessage(), true);
        } catch (SQLException e) {
            showDatabaseError(e);
        }
    }

    @FXML
    private void handleClearDates() {
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        handleRunReport();
    }

    @FXML
    private void handleExportCsv() {
        if (lastResult == null || lastResult.rows().isEmpty()) {
            showStatus("Run a report with data before exporting.", true);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export report");
        chooser.setInitialFileName(lastResult.title().toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = chooser.showSaveDialog(reportTable.getScene().getWindow());
        if (file == null) {
            return;   // user cancelled
        }

        try (BufferedWriter out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            out.write(toCsvLine(new ArrayList<>(lastResult.columns())));
            out.newLine();
            for (List<Object> row : lastResult.rows()) {
                out.write(toCsvLine(row));
                out.newLine();
            }
            showStatus("Exported to " + file.getName() + ".", false);
        } catch (IOException e) {
            showStatus("Could not write the file: " + e.getMessage(), true);
        }
    }

    // ---------- helpers ----------

    /** Builds the table's columns from the report's column names. */
    private void showResult(ReportResult result) {
        lastResult = result;
        reportTable.getColumns().clear();

        for (int i = 0; i < result.columns().size(); i++) {
            final int index = i;
            TableColumn<List<Object>, Object> column = new TableColumn<>(result.columns().get(i));
            column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().get(index)));

            // Display formatting only; sorting by clicking a header still uses the raw values
            column.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : format(item));
                    setAlignment(item instanceof Number ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                }
            });
            reportTable.getColumns().add(column);
        }
        reportTable.setItems(FXCollections.observableArrayList(result.rows()));
    }

    private void updateControls() {
        ReportType type = reportCombo.getValue();
        reportDescriptionLabel.setText(type == null ? "" : type.getDescription());
        boolean dates = type != null && type.usesDateRange();
        fromDatePicker.setDisable(!dates);
        toDatePicker.setDisable(!dates);
        limitSpinner.setDisable(type == null || !type.usesLimit());
        minOrdersSpinner.setDisable(type == null || !type.usesMinOrders());
    }

    private String format(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(DATE_TIME);
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.format(DATE_TIME);
        }
        return value.toString();
    }

    private String toCsvLine(List<?> values) {
        List<String> cells = new ArrayList<>();
        for (Object v : values) {
            String text = v == null ? "" : format(v);
            if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
                text = "\"" + text.replace("\"", "\"\"") + "\"";
            }
            cells.add(text);
        }
        return String.join(",", cells);
    }

    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle(isError ? "-fx-text-fill: #c0392b;" : "-fx-text-fill: #1e8449;");
    }

    private void showDatabaseError(SQLException e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Database error");
        alert.setHeaderText("Could not run the report");
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}

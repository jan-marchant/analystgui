/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.DoubleStringConverter;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.gui.controls.FractionCanvas;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.RNALabels;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RNAManagedListSceneController implements Initializable {

    //static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    @FXML
    private ToolBar toolBar;
    @FXML
    private TableView<LabelDataset> tableView;

    //private int dimNumber = 0;
    //private int maxDim = 6;
    //TableColumn dim1Column;
    //Button valueButton;
    //Button saveParButton;
    //Button closeButton;
    //Stage valueStage = null;
    //TableView<DatasetsController.ValueItem> valueTableView = null;
    //Dataset valueDataset = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTable();
    }

    public Stage getStage() {
        return stage;
    }

    public static RNAManagedListSceneController create() {
        FXMLLoader loader = new FXMLLoader(RNAManagedListSceneController.class.getResource("/fxml/RNAManagedListScene.fxml"));
        RNAManagedListSceneController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((Pane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<RNAManagedListSceneController>getController();
            controller.stage = stage;
            stage.setTitle("Managed List Setup");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    class DatasetStringFieldTableCell extends TextFieldTableCell<Dataset, String> {

        DatasetStringFieldTableCell(StringConverter converter) {
            super(converter);
        }

        @Override
        public void commitEdit(String newValue) {
            String column = getTableColumn().getText();
            LabelDataset dataset = (LabelDataset) getTableRow().getItem();
            super.commitEdit(newValue);
            switch (column) {
                case "labelString":
                    dataset.setLabelString(newValue);
                    break;
                case "managedList":
                    dataset.setManagedList(newValue);
                    break;
                case "condition":
                    dataset.setCondition(newValue);
                    break;
            }
        }
    }

    void initTable() {
        StringConverter sConverter = new DefaultStringConverter();
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<LabelDataset, String> fileNameCol = new TableColumn<>("Dataset");
        fileNameCol.setCellValueFactory(new PropertyValueFactory("dataset"));
        fileNameCol.setPrefWidth(200);
        fileNameCol.setEditable(false);

        TableColumn<LabelDataset, String> labelCol = new TableColumn<>("Labeling");
        labelCol.setCellValueFactory(new PropertyValueFactory("labelString"));
        labelCol.setPrefWidth(200);
        labelCol.setEditable(false);

        /*TableColumn<Dataset, String> listCol = new TableColumn<>("Managed List");
        listCol.setCellValueFactory(new PropertyValueFactory("manList"));
        listCol.setPrefWidth(200);
        listCol.setEditable(true);*/
        TableColumn<LabelDataset, String> listCol = new TableColumn<>("Managed List");
        listCol.setCellFactory(tc -> new RNAManagedListSceneController.DatasetStringFieldTableCell(sConverter));
        listCol.setCellValueFactory((TableColumn.CellDataFeatures<Dataset, String> p) -> {
            LabelDataset dataset = p.getValue();
            String label = dataset.getManagedList();
            return new ReadOnlyObjectWrapper(label);
        });

        TableColumn<LabelDataset, String> condCol = new TableColumn<>("Condition");
        condCol.setCellValueFactory(new PropertyValueFactory("Condition"));
        condCol.setPrefWidth(200);
        condCol.setEditable(true);

        TableColumn<LabelDataset, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory("Active"));
        activeCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        activeCol.setPrefWidth(75);
        activeCol.setMaxWidth(75);
        activeCol.setResizable(false);

        tableView.getColumns().setAll(fileNameCol, labelCol, listCol,condCol,activeCol);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void setDatasetList(ObservableList<LabelDataset> datasets) {
        if (tableView == null) {
            System.out.println("null table");
        } else {
            tableView.setItems(datasets);
        }

    }

    void refresh() {
        tableView.refresh();
    }

}

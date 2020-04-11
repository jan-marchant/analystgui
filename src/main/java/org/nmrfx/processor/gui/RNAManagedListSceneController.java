/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nmrfx.processor.gui;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class RNAManagedListSceneController implements Initializable {

    //static final DecimalFormat formatter = new DecimalFormat();

    private Stage stage;
    //@FXML
    //private ToolBar toolBar;
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
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.getController();
            controller.stage = stage;
            stage.setTitle("Managed List Setup");
            stage.show();
        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }

        return controller;

    }

    static class DatasetStringFieldTableCell extends TextFieldTableCell<LabelDataset, String> {

        DatasetStringFieldTableCell(StringConverter<String> converter) {
            super(converter);
        }

        @Override
        public void commitEdit(String newValue) {
            String column = getTableColumn().getText();
            LabelDataset dataset = (LabelDataset) getTableRow().getItem();
            super.commitEdit(newValue);
            switch (column) {
                case "Labeling":
                    dataset.setLabelScheme(newValue);
                    break;
                case "Managed List":
                    dataset.setManagedListName(newValue);
                    break;
                case "Condition":
                    dataset.setCondition(newValue);
                    break;
            }
        }
    }

    void initTable() {
        StringConverter<String> sConverter = new DefaultStringConverter();
        tableView.setEditable(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<LabelDataset, String> fileNameCol = new TableColumn<>("Dataset");
        fileNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        fileNameCol.setPrefWidth(200);
        fileNameCol.setEditable(false);

        //TODO: on dbl click open up RNA Labels
        TableColumn<LabelDataset, String> labelCol = new TableColumn<>("Labeling");
        labelCol.setCellValueFactory(new PropertyValueFactory<>("labelScheme"));
        labelCol.setPrefWidth(200);
        labelCol.setEditable(false);
        labelCol.setCellFactory(new Callback<TableColumn<LabelDataset, String>, TableCell<LabelDataset, String>>() {
            @Override
            public TableCell<LabelDataset, String> call(TableColumn<LabelDataset, String> col) {
                final TableCell<LabelDataset, String> cell = new TableCell<LabelDataset, String>() {
                    @Override
                    public void updateItem(String labelScheme, boolean empty) {
                        super.updateItem(labelScheme, empty);
                        if (empty) {
                            setText(null);
                        } else {
                            setText(labelScheme);
                        }
                    }
                };
                cell.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                    if (event.getClickCount() > 1) {
                        LabelDataset ld=(LabelDataset) cell.getTableRow().getItem();
                        ld.setupLabels();
                    }
                });
                return cell;
            }
        });


        /*TableColumn<Dataset, String> listCol = new TableColumn<>("Managed List");
        listCol.setCellValueFactory(new PropertyValueFactory("manList"));
        listCol.setPrefWidth(200);
        listCol.setEditable(true);*/
        TableColumn<LabelDataset, String> listCol = new TableColumn<>("Managed List");
        listCol.setCellFactory(tc -> new DatasetStringFieldTableCell(sConverter));
        listCol.setCellValueFactory(new PropertyValueFactory<>("managedListName"));
        /*listCol.setCellValueFactory((TableColumn.CellDataFeatures<LabelDataset, String> p) -> {
            LabelDataset dataset = p.getValue();
            String label = dataset.getManagedListName();
            return new ReadOnlyObjectWrapper(label);
        });*/
        listCol.setPrefWidth(225);

        TableColumn<LabelDataset, String> condCol = new TableColumn<>("Condition");
        condCol.setCellFactory(tc -> new DatasetStringFieldTableCell(sConverter));
        condCol.setCellValueFactory(new PropertyValueFactory<>("condition"));
        condCol.setPrefWidth(200);
        condCol.setEditable(true);

        TableColumn<LabelDataset, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(new PropertyValueFactory<>("active"));
        activeCol.setCellFactory(tc -> new CheckBoxTableCell<>());
        activeCol.setPrefWidth(75);
        activeCol.setMaxWidth(75);
        activeCol.setResizable(false);

        tableView.getColumns().setAll(fileNameCol, labelCol, listCol,condCol,activeCol);
        //tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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

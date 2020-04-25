package org.nmrfx.processor.gui;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.processor.gui.FXMLController;
import org.nmrfx.processor.gui.MinerController;
import org.nmrfx.processor.gui.RegionController;
import org.nmrfx.structure.chemistry.Entity;
import org.nmrfx.structure.chemistry.InvalidMoleculeException;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.Polymer;
import org.nmrfx.structure.chemistry.predict.Predictor;
import org.nmrfx.structure.chemistry.predict.ProteinPredictor;

/**
 * FXML Controller class
 *
 * @author brucejohnson
 */
public class PredictorSceneController implements Initializable {

    Stage stage = null;
    AtomController atomController;
    @FXML
    ChoiceBox<String> targetType;
    @FXML
    ChoiceBox<Integer> targetChoice;
    @FXML
    ChoiceBox<String> proteinChoice;
    @FXML
    ChoiceBox<String> rnaChoice;
    @FXML
    ChoiceBox<String> molChoice;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        targetType.getItems().addAll("Ref Set", "PPM Set");
        targetType.setValue("Ref Set");
        targetChoice.getItems().addAll(0, 1, 2, 3, 4);
        targetChoice.setValue(0);
        proteinChoice.getItems().addAll("Off", "3D");
        proteinChoice.setValue("3D");
        rnaChoice.getItems().addAll("Off", "Attributes", "3D-Dist", "3D-RC");
        rnaChoice.setValue("Attributes");
        molChoice.getItems().addAll("Off", "Shells");
        molChoice.setValue("Shells");
    }

    public static PredictorSceneController create(AtomController atomController) {
        FXMLLoader loader = new FXMLLoader(MinerController.class.getResource("/fxml/PredictorScene.fxml"));
        PredictorSceneController controller = null;
        Stage stage = new Stage(StageStyle.DECORATED);
        try {
            Scene scene = new Scene((BorderPane) loader.load());
            stage.setScene(scene);
            scene.getStylesheets().add("/styles/Styles.css");

            controller = loader.<PredictorSceneController>getController();
            controller.stage = stage;
            controller.atomController = atomController;
            stage.setTitle("Predictor");
            stage.setScene(scene);
//            stage.setMinWidth(200);
//            stage.setMinHeight(250);
            stage.show();
            stage.toFront();

        } catch (IOException ioE) {
            ioE.printStackTrace();
            System.out.println(ioE.getMessage());
        }
        return controller;
    }

    public Stage getStage() {
        return stage;
    }

    @FXML
    public void predict() {
        Molecule molecule = Molecule.getActive();
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }

        try {
            int target = targetChoice.getValue();
            int ppmSet = 0;
            if (targetType.getValue().equals("Ref Set")) {
                ppmSet = -target - 1;
            } else {
                ppmSet = target;
            }
            predictMolecule(molecule, ppmSet);
            atomController.refreshAtomTable();
        } catch (InvalidMoleculeException | IOException ex) {
            ExceptionDialog dialog = new ExceptionDialog(ex);
            dialog.showAndWait();
        }

    }

    boolean checkDotBracket(Molecule molecule) {
        if (molecule.getDotBracket().equals("")) {
            TextInputDialog textDialog = new TextInputDialog("Enter dot-bracket sequence");
            Optional<String> result = textDialog.showAndWait();
            if (result.isPresent()) {
                String dotBracket = result.get().trim();
                if (dotBracket.equals("")) {
                    return false;
                }
                molecule.setDotBracket(dotBracket);
            } else {
                return false;
            }
        }
        return true;
    }

    boolean checkCoordinates(Molecule molecule) {
        if (molecule == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
            return false;
        } else if (molecule.structures.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule coordinates", ButtonType.CLOSE);
            alert.showAndWait();
            return false;
        } else {
            return true;
        }

    }

    public void predictMolecule(Molecule mol, int ppmSet) throws InvalidMoleculeException, IOException {
        if (mol == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No molecule present", ButtonType.CLOSE);
            alert.showAndWait();
            return;
        }
        Predictor predictor = new Predictor();

        boolean hasPeptide = false;

        for (Polymer polymer : mol.getPolymers()) {
            if (polymer.isRNA()) {
                switch (rnaChoice.getValue()) {
                    case "3D-Dist":
                        if (checkCoordinates(mol)) {
                            predictor.predictRNAWithDistances(polymer, 0, ppmSet, false);
                        }
                        break;
                    case "3D-RC":
                        if (checkCoordinates(mol)) {
                            predictor.predictRNAWithRingCurrent(polymer, 0, ppmSet);
                        }
                        break;
                    case "Attributes":
                        if (checkDotBracket(mol)) {
                            predictor.predictRNAWithAttributes(ppmSet);
                        }
                        break;
                    default:
                        break;
                }
            } else if (polymer.isPeptide()) {
                hasPeptide = true;
            }
        }

        if (hasPeptide) {
            if (proteinChoice.getValue().equals("3D")) {
                if (checkCoordinates(mol)) {
                    predictor.predictProtein(mol, ppmSet);
                }
            }
        }
        boolean hasPolymer = !mol.getPolymers().isEmpty();
        for (Entity entity : mol.getLigands()) {
            if (molChoice.getValue().equals("Shells")) {
                predictor.predictWithShells(entity, ppmSet);
                if (hasPolymer) {
                    predictor.predictLigandWithRingCurrent(entity, ppmSet);
                }
            }
        }

    }

}

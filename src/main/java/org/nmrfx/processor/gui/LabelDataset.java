package org.nmrfx.processor.gui;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.RNALabels;

import java.util.*;

public class LabelDataset {
    /**
     * This class contains variables and methods for more easily keeping
     * track of active atoms in a given dataset
     * TODO: add some logic to project load (or later) to set them up
     */

    private SimpleStringProperty name;
    private SimpleStringProperty labelScheme;
    private SimpleStringProperty condition;
    private SimpleStringProperty managedListName;
    private SimpleBooleanProperty active;

    private Dataset dataset;

    //need to clear the map when the labelString changes. Implement a listener? Assuming it always changes through set then no need
    //What about if dataset property is updated, this will not get the update.
    private HashMap<Atom,Boolean> atomActive;
    //private String labelString;
    //private PeakList managedList;
                                    /**
                                    * Maybe should set up another type which extends PeakList and implements new functionality.
                                    * will need to copy the loaded peaklist into this new type with same name, delete original
                                    * but keep in the peakListTable so it can be selected and is saved.
                                    * but then can override certain functions.
                                    * will be tricky working out when to do the copying though
                                    * need some kind of validate function to check whether it's got out of hand?
                                    * TODO: Perhaps add a listener for whenever a dataset or peaklist is added. Check dataset
                                    *  properties to see whether more action needed.*/
    //private Boolean active;
    //private String managedListName;
    //private String condition;

    public static ObservableList<LabelDataset> labelDatasetTable = FXCollections.observableArrayList();

    //TODO:consider supporting >2D experiments
    public static PeakList masterList;

    static {
        String base="managed_master";
        int i=1;
        while (PeakList.get(base+ i)!=null) {
            i++;
        }
        masterList = new PeakList(base+ i,2);
        //don't want this to show up in peak browser etc. Maybe it should though?
        PeakList.peakListTable.remove(base+ i);
    }
    public static LabelDataset find(Dataset dataset) {
        //better with Optional? labelDatasetTable.stream().filter(member -> member.getName() == dataset.getName()).findFirst();
        for (LabelDataset ld : LabelDataset.labelDatasetTable) {
            if (dataset.getName().equals(ld.getDataset().getName())) {
                return ld;
            }
        }
        return null;
    }

    //Will want to add a listener? e.g. PeakList.peakListTable.addListener(mapChangeListener);
    //TODO: clear on Project close
    //  clear on dataset close
    //  update on dataset rename

    public LabelDataset (Dataset dataset) {
        this.dataset=dataset;
        this.name=new SimpleStringProperty(dataset.getName());
        this.active = new SimpleBooleanProperty(Boolean.parseBoolean(dataset.getProperty("active")));
        this.managedListName=new SimpleStringProperty(dataset.getProperty("managedList"));
        this.labelScheme = new SimpleStringProperty(dataset.getProperty("labelScheme"));
        this.condition = new SimpleStringProperty(dataset.getProperty("condition"));
        if (this.managedListName.get().equals("")) {
            setManagedListName("managed_"+dataset.getName().split("\\.")[0]);
        }

        if (this.condition.get().equals("")) {
            setCondition("managed_"+dataset.getName().split("\\.")[0]);
        }
        this.atomActive= new HashMap<>();

        this.active.addListener( (obs, ov, nv) -> this.setActive(nv));
        //active should only be set to true if peaklist is set.
        /*
        //Actually I think let's take care of this on first peak add
        if (dataset.getProperty("managedList") == "") {
            this.setActive(false);
        }

        if (this.active) {

            this.managedList = PeakList.get(this.managedListName);
            if (this.managedList==null) {
                this.managedList = new PeakList(dataset.getProperty("managedList"), dataset.getNDim());
                managedList.fileName = dataset.getFileName();
                for (int i = 0; i < dataset.getNDim(); i++) {
                    int dDim = i;
                    SpectralDim sDim = managedList.getSpectralDim(i);
                    sDim.setDimName(dataset.getLabel(dDim));
                    sDim.setSf(dataset.getSf(dDim));
                    sDim.setSw(dataset.getSw(dDim));
                    sDim.setSize(dataset.getSize(dDim));
                    double minTol = Math.round(100 * 2.0 * dataset.getSw(dDim) / dataset.getSf(dDim) / dataset.getSize(dDim)) / 100.0;
                    double tol = minTol;
                    Nuclei nuc = dataset.getNucleus(dDim);
                    if (null != nuc) {
                        switch (nuc) {
                            case H1:
                                tol = 0.05;
                                break;
                            case C13:
                                tol = 0.6;
                                break;
                            case N15:
                                tol = 0.2;
                                break;
                            default:
                                tol = minTol;
                        }
                    }
                    tol = Math.min(tol, minTol);

                    sDim.setIdTol(tol);
                    sDim.setDataDim(dDim);
                    sDim.setNucleus(dataset.getNucleus(dDim).getNumberName());
                }
            }
        }*/
        //Collection<Molecule> molecules = StructureProject.getActive().getMolecules();
        //RNALabels rnaLabels = new RNALabels();
        //for (Molecule molecule : molecules) {
        //    rnaLabels.parseSelGroupsD(dataset, molecule, labelString);
        //}
        labelDatasetTable.add(this);
    }

    public Dataset getDataset() {
        return this.dataset;
    }

    public String getName() {
        return this.name.get();
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public String getLabelScheme() {
        return this.labelScheme.get();
    }

    public void setLabelScheme(String labelScheme) {
        if (!labelScheme.equals(getLabelScheme())) {
            Optional<ButtonType> result;
            if (this.labelScheme.get().equals("")) {
                result = Optional.of(ButtonType.OK);
            } else {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Label Scheme Changed");
                alert.setHeaderText("Clear all labelling information for "+ this.getName() + "?");
                alert.setContentText("This includes deleting its managed peaklist (" + this.getManagedListName() + ") if it exists.");

                result = alert.showAndWait();
            }
            if (result.get() == ButtonType.OK) {
                PeakList.remove(this.getManagedListName());
                if (labelScheme.isEmpty()) {
                    //Is this worth it? Could just set inactive?
                    labelDatasetTable.remove(LabelDataset.this);
                    dataset.removeProperty("active");
                    dataset.removeProperty("labelScheme");
                    dataset.removeProperty("managedList");
                    dataset.removeProperty("condition");
                    /*name=null;
                    this.labelScheme =null;
                    condition=null;
                    managedListName=null;
                    active=null;
                    dataset=null;
                    atomActive=null;*/
                } else {
                    //Prompt are you sure. If yes then
                    //remove all peaks from managed list
                    //Reset hashmap when labelString updated
                    this.atomActive.clear();
                    this.labelScheme.set(labelScheme);
                    dataset.addProperty("labelScheme", labelScheme);
                    this.updatePeaks();
                }
                dataset.writeParFile();
            }
        }
    }

    /*public PeakList getManagedList() {
        return managedList;
    }

    public void setManagedList(PeakList managedList) {
        this.managedList = managedList;
        dataset.addProperty("managedList", managedList.getName());
        dataset.writeParFile();
    }*/

    public void updatePeaks() {

    }
    public String getManagedListName() {
        return this.managedListName.get();
    }

    public void setManagedListName(String managedListName) {
        //this.managedList = PeakList.get(managedListName);
        this.managedListName.set(managedListName);
        dataset.addProperty("managedList", managedListName);
        dataset.writeParFile();
    }

    public String getCondition() {
        return this.condition.get();
        //return managedList.getSampleConditionLabel();
    }

    public void setCondition(String condition) {
        this.condition.set(condition);
        dataset.addProperty("condition", condition);
        dataset.writeParFile();
        //managedList.setSampleConditionLabel(condition);
    }

    public SimpleBooleanProperty activeProperty() {
        return this.active;
    }

    public SimpleStringProperty labelSchemeProperty() {
        return this.labelScheme;
    }

    public Boolean isActive() {
        return this.active.get();
    }

    public void setActive(Boolean active) {
        this.active.set(active);
        dataset.addProperty("active", Boolean.toString(active));
        dataset.writeParFile();
    }

    public void setupLabels() {
        if (AnalystApp.rnaPeakGenController == null) {
            AnalystApp.rnaPeakGenController = RNAPeakGeneratorSceneController.create();
        }
        if (AnalystApp.rnaPeakGenController != null) {
            AnalystApp.rnaPeakGenController.getStage().show();
            AnalystApp.rnaPeakGenController.getStage().toFront();
        } else {
            System.out.println("Couldn't make rnaPeakGenController ");
        }
        AnalystApp.rnaPeakGenController.setDataset(this.getName());
    }

    //is storing the hashmap worth the complication? Is there a better way to cache?
    //print(timeit.timeit("ld.isAtomActive(atom)","from org.nmrfx.structure.chemistry import Molecule;from org.nmrfx.processor.gui import LabelDataset;ld=LabelDataset.labelDatasetTable.get(0);atom=Molecule.getAtomByName(\"1032.H2\")",number=10000000))
    // 2.351
    //print(timeit.timeit("RNALabels.isAtomInLabelString(atom, \"*:A*.Hn *:C*.Hr;*:50-52.Hn\")","from org.nmrfx.structure.chemistry import RNALabels,Molecule;atom=Molecule.getAtomByName(\"1032.H2\")
    // 10.004
    //Perhaps worth it for filtering peaklists
    public Boolean isAtomActive (Atom atom) {
        if (atom!=null) {
            Boolean active = atomActive.get(atom);
            if (active==null) {
                active = RNALabels.isAtomInLabelString(atom, this.labelScheme.get());
                atomActive.put(atom, active);
            }
            return active;
        } else {
            System.out.println("Couldn't find atom");
            return false;
        }
    }

    public Boolean isAtomActive (String atomString) {
        //Need error handling for atom doesn't exist I guess
        Atom atom;
        try {
            atom=Molecule.getAtomByName(atomString);
        } catch (Exception e) {
            System.out.println("No active molecule");
            return false;
        }
        if (atom!=null) {
            Boolean active = atomActive.get(atom);
            if (active==null) {
                active = RNALabels.isAtomInLabelString(atom, this.labelScheme.get());
                atomActive.put(atom, active);
            }
            return active;
        } else {
            System.out.println("Couldn't find atom");
            return false;
        }
    }
}

package org.nmrfx.processor.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetListener;
import org.nmrfx.processor.datasets.Nuclei;
import org.nmrfx.processor.datasets.peaks.*;
import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.RNALabels;

import java.util.*;

public class LabelDataset implements DatasetListener {
    /**
     * This class contains variables and methods for more easily keeping
     * track of active atoms in a given dataset
     */

    /*TODO: check with Bruce desired project close functionality
     *  presently datasets aren't closed, and so these remain
     *  also main stage remains (with datasets drawn)
     */

    private SimpleStringProperty name;
    private SimpleStringProperty labelScheme;
    private SimpleStringProperty condition;
    private SimpleStringProperty managedListName;
    private ManagedList managedList;
    private SimpleBooleanProperty active;

    private Dataset dataset;

    //need to clear the map when the labelString changes. Implement a listener? Assuming it always changes through set then no need
    //What about if dataset property is updated directly? This will not get the update. Could lead to inconsistencies.
    private HashMap<Atom,Boolean> atomActive;
    private HashMap<Atom,Integer> atomPercent;
    /**
    * Maybe should set up another type which extends PeakList and implements new functionality.
    * will need to copy the loaded peaklist into this new type with same name, delete original
    * but keep in the peakListTable so it can be selected and is saved.
    * but then can override certain functions.
    * will be tricky working out when to do the copying though
    * need some kind of validate function to check whether it's got out of hand?
    * TODO: Perhaps add a listener for whenever a peaklist is added.
     * */

    public static ObservableList<LabelDataset> labelDatasetTable = FXCollections.observableArrayList();
    private MapChangeListener<String, PeakList> peakmapChangeListener;

    public static LabelDataset find(Dataset dataset) {
        //better with Optional? labelDatasetTable.stream().filter(member -> member.getName() == dataset.getName()).findFirst();
        for (LabelDataset ld : LabelDataset.labelDatasetTable) {
            if (dataset.getName().equals(ld.getDataset().getName())) {
                return ld;
            }
        }
        return null;
    }

    public static LabelDataset findByName(String name) {
        //better with Optional? labelDatasetTable.stream().filter(member -> member.getName() == dataset.getName()).findFirst();
        for (LabelDataset ld : LabelDataset.labelDatasetTable) {
            if (name.equals(ld.getName())) {
                return ld;
            }
        }
        return null;
    }


    //Will want to add a listener? e.g. PeakList.peakListTable.addListener(mapChangeListener);

    public static LabelDataset getMaster() {
        return master;
    }

    public static ManagedList getMasterList() {
        ManagedList masterList=master.managedList;
        if (masterList==null) {
            PeakList.peakListTable.removeListener(master.peakmapChangeListener);
            //TODO: any need to consider more than 5 dims?
            masterList=new ManagedList(getMaster(),5);
            master.managedList=masterList;
            //might need something like this - not sure how D1 / D2 will behave
            //getMaster().initializeList();
        }
        return masterList;
    }

    private static volatile LabelDataset master = new LabelDataset() {
        @Override
        public Boolean isAtomActive (Atom atom) {
            if (atom != null) {
                return true;
            }
            return false;
        }
        @Override
        public Integer getAtomPercent (Atom atom) {
            if (atom!=null) {
                return 100;
            } else {
                System.out.println("Couldn't find atom");
                return 0;
            }
        }

        @Override
        public float getPeakPercent (Peak peak) {
            return 100;
        }

        @Override
        protected void delete() {
            System.out.println("Not allowed for master");
        }

        @Override
        public void setDataset(Dataset dataset) {
            System.out.println("Not allowed for master");
        }

        @Override
        public void setManagedListName(String managedListName) {
            System.out.println("Not allowed for master");
        }

        @Override
        public void setCondition(String condition) {
            System.out.println("Not allowed for master");
        }

        @Override
        public void setActive(Boolean active) {
            System.out.println("Not allowed for master");
        }

        @Override
        public void setupLabels() {
            System.out.println("Not allowed for master");
        }

        @Override
        public ManagedList getManagedList() {
            //if list doesn't exist, this is where to create it, after first deleting listener
            return LabelDataset.getMasterList();
        }
    };

    private LabelDataset () {
        this.dataset = null;
        this.name = new SimpleStringProperty("master");
        this.active = new SimpleBooleanProperty(Boolean.parseBoolean("true"));
        //fixme magic peaklist name!
        this.managedListName = new SimpleStringProperty("master_managed");
        this.managedList=null;
        this.labelScheme = new SimpleStringProperty("");
        this.condition = new SimpleStringProperty("master_managed");

        peakmapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            stealPeaklist();
        };

        PeakList.peakListTable.addListener(peakmapChangeListener);

        //labelDatasetTable.add(this);
    }


    public LabelDataset (Dataset dataset) {
        this.dataset=dataset;
        this.name=new SimpleStringProperty(dataset.getName());
        //on creation, active is false, on load active is true. Use as flag for updating peak lists? i.e. if changed from true to false, delete peaklist. if from false to true, create and update peaklist (as managedList).
        //if loaded as true here then must already exist in project, so wait for peaklist to be loaded and set up a listener. listener removes itself when it finds and copies its list as a managedList.
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
        this.atomPercent= new HashMap<>();

        this.active.addListener( (obs, ov, nv) -> this.setActive(nv));

        Platform.runLater(() -> {
            Dataset.addObserver(this);
                }
        );

        peakmapChangeListener = (MapChangeListener.Change<? extends String, ? extends PeakList> change) -> {
            stealPeaklist();
        };

        PeakList.peakListTable.addListener(peakmapChangeListener);


        labelDatasetTable.add(this);
    }

    private void stealPeaklist () {
        PeakList pl=PeakList.get(this.getManagedListName());
        if (pl!=null) {
            PeakList.peakListTable.removeListener(peakmapChangeListener);
            //fixme need to wait for peaklist to be fully read before stealing it
            Platform.runLater(() -> {
                managedList = new ManagedList(this, pl);
            }
            );
        }
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
                if (labelScheme.isEmpty()) {
                    this.delete();
                } else {
                    PeakList.remove(this.getManagedListName());
                    this.atomActive.clear();
                    this.atomPercent.clear();
                    this.labelScheme.set(labelScheme);
                    dataset.addProperty("labelScheme", labelScheme);
                    dataset.writeParFile();
                    managedList=new ManagedList(this);
                    this.initializeList();
                    this.updatePeaks();
                }
            }
        }
    }

    public ManagedList getManagedList() {
        if (managedList==null) {
            //This could happen if labeling set up, and active set, but project not saved with new peaklist
            //This is possibly a reason not to write pars so aggressively, but we would still be at the mercy of
            //user initiated peak par writing without then saving.
            //TODO: consider whether project should be saved when user initiates a par write? It can lead
            // to issues with referencing etc. as well as this problem otherwise
            PeakList.peakListTable.removeListener(peakmapChangeListener);
            managedList=new ManagedList(this);
            initializeList();
            updatePeaks();
        }

        return managedList;
    }
    /*
    public void setManagedList(ManagedList managedList) {
        this.managedList = managedList;
        dataset.addProperty("managedList", managedList.getName());
        dataset.writeParFile();
    }*/

    public void updatePeaks() {
        for (Peak peak : getMasterList().peaks()) {
            //fixme: this doesn't work - e.g. may miss diagonals
            managedList.addLinkedPeak(peak, 100);
        }
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

    public void initializeList() {
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

    public void setActive(Boolean active) {
        if (!active) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Deactivate managed list?");
            alert.setHeaderText("Clear managed list for " + this.getName() + "?");
            alert.setContentText("This includes deleting its managed peaklist (" + this.getManagedListName() + ") if it exists.");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.get() == ButtonType.OK) {
                this.active.set(active);
                PeakList.remove(this.getManagedListName());
                dataset.addProperty("active", Boolean.toString(active));
                dataset.writeParFile();
            } else {
                this.active.set(true);
            }
        } else {
            this.active.set(true);
            dataset.addProperty("active", Boolean.toString(true));
            dataset.writeParFile();
            PeakList.peakListTable.removeListener(peakmapChangeListener);
            managedList=new ManagedList(this);
            this.initializeList();
            this.updatePeaks();
        }
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
            Boolean l_active = atomActive.get(atom);
            if (l_active==null) {
                l_active = RNALabels.isAtomInLabelString(atom, this.labelScheme.get());
                atomActive.put(atom, l_active);
            }
            return l_active;
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
        return isAtomActive(atom);
    }

    public Integer getAtomPercent (Atom atom) {
        if (atom!=null) {
            Integer percent = atomPercent.get(atom);
            if (percent==null) {
                percent = RNALabels.atomPercentLabelString(atom, this.labelScheme.get());
                if (percent>100) {
                    System.out.println("Check labeling string - "+atom.getName()+" is apparently "+percent+"% labeled");
                    percent=100;
                }
                atomPercent.put(atom, percent);
            }
            return percent;
        } else {
            System.out.println("Couldn't find atom");
            return 0;
        }
    }

    public Integer getAtomPercent (String atomString) {
        //Need error handling for atom doesn't exist I guess
        Atom atom;
        try {
            atom=Molecule.getAtomByName(atomString);
        } catch (Exception e) {
            System.out.println("No active molecule");
            return 0;
        }
        return getAtomPercent(atom);
    }

    public float getPeakPercent(Peak peak) {
        //TODO: support labeling schemes like A28/Ar (i.e. should not see A2/8-Ar NOEs from same residue in same molecule
        // means subtle difference in label string parsing

        //TODO: Add GUI support for setting labeling percent
        Float total=null;
        float atomFraction;
        AtomResonance resonance;
        for (PeakDim peakDim : peak.getPeakDims()) {
            resonance = (AtomResonance) peakDim.getResonance();
            atomFraction=(float) getAtomPercent(resonance.getAtom()) /100;
            if (total==null) {
                total=atomFraction;
            } else {
                total*=atomFraction;
            }
        }
        return total==null?0:100*total;
    }

    protected void delete() {
        ManagedList.remove(this.getManagedListName());
        Platform.runLater(() -> {
            Dataset.removeObserver(this);
                }
        );
        labelDatasetTable.remove(LabelDataset.this);
        dataset.removeProperty("active");
        dataset.removeProperty("labelScheme");
        dataset.removeProperty("managedList");
        dataset.removeProperty("condition");
        dataset.writeParFile();
        if (labelDatasetTable.isEmpty()) {
            ManagedList.remove(LabelDataset.getMaster().getManagedListName());
        }
    }

    @Override
    public void datasetAdded(Dataset dataset) {
    }

    @Override
    public void datasetModified(Dataset dataset) {
    }

    @Override
    public void datasetRemoved(Dataset dataset) {
        if (dataset==this.dataset) {
            if (Platform.isFxApplicationThread()) {
                this.delete();
            } else {
                Platform.runLater(() -> {
                    this.delete();
                        }
                );
            }
        }
    }

    @Override
    public void datasetRenamed(Dataset dataset) {
        if (dataset==this.dataset) {
            if (Platform.isFxApplicationThread()) {
                this.name.set(dataset.getName());
            } else {
                Platform.runLater(() -> {
                    this.name.set(dataset.getName());
                        }
                );
            }
        }
    }
    public static void parseNew(Dataset dataset) {
        if (find(dataset)==null) {
            if (dataset.getProperty("labelScheme")!="") {
                new LabelDataset(dataset);
            }
        }
    }
}

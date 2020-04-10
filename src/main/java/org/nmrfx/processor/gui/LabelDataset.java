package org.nmrfx.processor.gui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.DatasetParameterFile;
import org.nmrfx.processor.datasets.Nuclei;
import org.nmrfx.processor.datasets.peaks.PeakList;
import org.nmrfx.processor.datasets.peaks.SpectralDim;
import org.nmrfx.project.StructureProject;
import org.nmrfx.structure.chemistry.Molecule;
import org.nmrfx.structure.chemistry.RNALabels;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;

public class LabelDataset {
    /**
     * This class contains variables and methods for more easily keeping
     * track of active atoms in a given dataset
     * TODO: add some logic to project load (or later) to set them up
     */

    private Dataset dataset;
    private String labelString;
    private PeakList managedList;   /**
                                    * Maybe should set up another type which extends PeakList and implements new functionality.
                                    * will need to copy the loaded peaklist into this new type with same name, delete original
                                    * but keep in the peakListTable so it can be selected and is saved.
                                    * but then can override certain functions.
                                    * will be tricky working out when to do the copying though
                                    * need some kind of validate function to check whether it's got out of hand?
                                    * TODO: Perhaps add a listener for whenever a dataset or peaklist is added. Check dataset
                                    *  properties to see whether more action needed.*/
    private Boolean active;

    public static ObservableList<LabelDataset> labelDatasetTable = FXCollections.observableArrayList();

    //Will want to add a listener? e.g. PeakList.peakListTable.addListener(mapChangeListener);

    public LabelDataset (Dataset dataset) {
        this.dataset=dataset;
        this.active = Boolean.parseBoolean(dataset.getProperty("active"));
        //active should only be set to true if peaklist is set.
        if (dataset.getProperty("peakList") == "") {
            this.setActive(false);
        }
        this.labelString = dataset.getProperty("labelScheme");

        if (this.active) {
            this.managedList = PeakList.get(dataset.getProperty("peakList"));
            if (this.managedList==null) {
                this.managedList = new PeakList(dataset.getProperty("peakList"), dataset.getNDim());
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
        }
        Collection<Molecule> molecules = StructureProject.getActive().getMolecules();
        RNALabels rnaLabels = new RNALabels();
        for (Molecule molecule : molecules) {
            rnaLabels.parseSelGroups(dataset, molecule, labelString);
        }
        labelDatasetTable.add(this);
    }

    public Dataset getDataset() {
        return dataset;
    }

    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public String getLabelString() {
        return labelString;
    }

    public void setLabelString(String labelString) {
        this.labelString = labelString;
        dataset.addProperty("labelString", labelString);
        dataset.writeParFile();
    }

    public PeakList getManagedList() {
        return managedList;
    }

    public void setManagedList(PeakList managedList) {
        this.managedList = managedList;
        dataset.addProperty("managedList", managedList.getName());
        dataset.writeParFile();
    }

    public String getCondition() {
        return managedList.getSampleConditionLabel()
    }

    public void setCondition(String condition) {
        managedList.setSampleConditionLabel(condition);
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
        dataset.addProperty("active", Boolean.toString(active));
        dataset.writeParFile();
    }

}

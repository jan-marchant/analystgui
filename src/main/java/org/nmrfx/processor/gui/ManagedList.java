package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.Dataset;
import org.nmrfx.processor.datasets.peaks.*;

import org.nmrfx.structure.chemistry.Atom;
import org.nmrfx.structure.chemistry.Molecule;

import java.util.List;

public class ManagedList extends PeakList {
    private LabelDataset labelDataset;
    //SNR required for picking peak
    private static double detectionlimit=3;

    public ManagedList(LabelDataset labelDataset) {
        super(labelDataset.getManagedListName(),labelDataset.getDataset().getNDim());
        this.labelDataset=labelDataset;
    }
    public ManagedList(LabelDataset labelDataset, int n) {
        super(labelDataset.getManagedListName(),n);
        this.labelDataset=labelDataset;
    }
    public ManagedList(LabelDataset labelDataset,PeakList peakList) {
        super(labelDataset.getManagedListName()+"temp",labelDataset.getDataset().getNDim());
        //peakList.copy(labelDataset.getManagedListName()+"temp",true,false,true,false);
        //might not be necessary as next line should crunch

        this.searchDims.addAll(peakList.searchDims);
        this.fileName = peakList.fileName;
        this.scale = peakList.scale;
        this.setDetails(peakList.getDetails());
        this.setSampleLabel(peakList.getSampleLabel());
        this.setSampleConditionLabel(peakList.getSampleConditionLabel());

        for (int i = 0; i < nDim; i++) {
            this.setSpectralDim(peakList.getSpectralDim(i).copy(this),i);
        }

        for (int i = 0; i < peakList.peaks().size(); i++) {
            Peak peak = peakList.peaks().get(i);
            Peak newPeak = peak.copy(this);
            newPeak.setIdNum(peak.getIdNum());
            newPeak.initPeakDimContribs();
            peaks().add(newPeak);
            clearIndex();

            peak.copyLabels(newPeak);

            for (int j = 0; j < peak.peakDims.length; j++) {
                PeakDim peakDim1 = peak.peakDims[j];
                PeakDim peakDim2 = newPeak.peakDims[j];
                PeakList.linkPeakDims(peakDim1, peakDim2);
            }
        }
        this.idLast = peakList.idLast;
        this.reIndex();

        peakList.remove();
        this.setName(labelDataset.getManagedListName());
        this.labelDataset=labelDataset;
    }
    @Override
    public Peak addPeak(Peak newPeak) {
        //will this need to be a ManagedPeak class to keep proper track of modifications?
        //fixme: what about when getNewPeak called? need to be careful
        //TODO: consider behavior when Resonances are SimpleResonance instead of AtomResonance
        //TODO: Ensure same assignments not allocated twice
        //TODO: Handle peak chemical shifts according to freeze thaw status
        //TODO: Set slide condition etc.
        //TODO: Set slideable etc.
        //TODO: Add diagonal peak - as a linked peak? Gives opportunity for not adding if
        // later evolution of labelstring allows / suggests

        newPeak.initPeakDimContribs();
        Boolean showPicker=false;
        AtomResonance resonance;
        for (PeakDim peakDim : newPeak.getPeakDims()) {
            resonance=(AtomResonance) peakDim.getResonance();
            if (resonance.getAtom()==null) {
                showPicker=true;
            }
        }
        //TODO: Check compatibility of Bruce's peak assigner - does it filter by labeling? I guess I filter next anyway?
        if (showPicker) {
            PeakAtomPicker peakAtomPicker = new PeakAtomPicker();
            peakAtomPicker.create();
            peakAtomPicker.showAndWait(300, 300, newPeak);
            //peakpicker doAssign() only sets labels - possible fixme
            for (PeakDim peakDim : newPeak.getPeakDims()) {
                resonance=(AtomResonance) peakDim.getResonance();
                //fixme: only considers active molecule
                resonance.setAtom(Molecule.getAtomByName(peakDim.getLabel()));
            }
        }

        float percent=labelDataset.getPeakPercent(newPeak);
        //don't repick existing peaks
        if (percent>0) {
            for (Peak peak : peaks()) {
                    int count=0;
                    for (int i=0;i<nDim;i++) {
                        if (((AtomResonance) newPeak.getPeakDim(i).getResonance()).getAtom()
                            ==((AtomResonance) peak.getPeakDim(i).getResonance()).getAtom()) {
                            count++;
                        }
                    }
                    if (count==nDim) {
                        //handle matching peak. for now just quit
                        return null;
                        //break;
                    }
            }

            //TODO: add check for whether peak already exists
            peaks().add(newPeak);
            clearIndex();
            //add diagonal
            Boolean diag=false;
            Peak dpeak = new Peak(this, nDim);
            if (nDim==2 &&
                    ((AtomResonance) newPeak.getPeakDim(0).getResonance()).getAtom().getElementNumber()==
                            ((AtomResonance) newPeak.getPeakDim(1).getResonance()).getAtom().getElementNumber()) {

                newPeak.copyTo(dpeak);
                dpeak.getPeakDim(1).setChemShiftValue(newPeak.getPeakDim(0).getChemShiftValue());
                dpeak.getPeakDim(0).setChemShiftValue(newPeak.getPeakDim(1).getChemShiftValue());
                //TODO: tweak bounds
                dpeak.getPeakDim(1).setResonance(newPeak.getPeakDim(0).getResonance());
                dpeak.getPeakDim(0).setResonance(newPeak.getPeakDim(1).getResonance());
                peaks().add(dpeak);
                clearIndex();
                diag=true;
            }
            //copy to other appropriate lists - use relative weights to set peak size
            //filter peaks is simply just a copy to the master list!
            //though need to overload with handling for no assignments
            //could be a "skip asking" variable read before bringing up the atompicker
            if (LabelDataset.getMaster()!=labelDataset) {
                LabelDataset.getMasterList().addLinkedPeak(newPeak, percent);
                if (diag) {
                    LabelDataset.getMasterList().addLinkedPeak(dpeak, percent);
                }
            }
            for (LabelDataset ld : LabelDataset.labelDatasetTable) {
                if (ld!=labelDataset && ld.isActive()) {
                    ld.getManagedList().addLinkedPeak(newPeak, percent);
                    if (diag) {
                        ld.getManagedList().addLinkedPeak(dpeak, percent);
                    }
                }
            }
            //TODO: update any active canvases
            return newPeak;
         }
        return null;
    }

    public void addLinkedPeak(Peak manPeak,float percent) {
        //TODO: add check for whether peak already exists
        float intensity=manPeak.getIntensity();
        float new_percent=labelDataset.getPeakPercent(manPeak);
        //this doesn't account for spin diffusion - only for "breakthrough" peaks
        float new_intensity=new_percent*intensity/percent;
        Boolean active=false;
        Dataset ds=Dataset.getDataset(fileName);
        if (ds!=null) {
            Double noise=ds.guessNoiseLevel();
            if (noise==null) {
                active=true;
            } else if (new_intensity>detectionlimit*noise) {
                active=true;
            }
        } else {
            //also add if no dataset
            active=true;
        }
        if (active) {
            Peak newManPeak=manPeak.copy(this);
            newManPeak.setIntensity(new_intensity);
            //TODO: change bounds of newManPeak to reflect new intensity
            //newManPeak.initPeakDimContribs();
            for (int i = 0; i < manPeak.getNDim(); i++) {
                newManPeak.getPeakDim(i).setResonance(manPeak.getPeakDim(i).getResonance());
            }
            peaks().add(newManPeak);
            clearIndex();
            //ensure resonances match
            //scale intensity
        }
    }
}

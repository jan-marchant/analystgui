package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.peaks.*;

public class ManagedPeak extends Peak {
    public ManagedPeak(int nDim) {
        super(nDim);
    }

    public ManagedPeak(PeakList peakList, int nDim) {
        super(peakList, nDim);
        this.initPeakDimContribs();
    }

    public ManagedPeak(PeakList peakList,Peak peak) {
        super(peakList,peak.getNDim());
        this.initPeakDimContribs();
        peak.copyTo(this);
        for (int i = 0; i < peak.getNDim(); i++) {
            this.getPeakDim(i).setResonance(peak.getPeakDim(i).getResonance());
            //TODO: Suggest to bruce this would be better in setResonance (only called in NMRStarReader I think)
            peak.getPeakDim(i).getResonance().add(this.getPeakDim(i));
        }
    }

    @Override
    public void setStatus(int status) {
        super.setStatus(status);
        if (status < 0) {
            //fully delete all equivalent peaks.
            for (LabelDataset ld : LabelDataset.labelDatasetTable) {
                ld.getManagedList().deleteMatchingPeaks(this);
            }
            for (PeakDim peakDim : this.peakDims) {
                peakDim.remove();
                if (peakDim.hasMultiplet()) {
                    Multiplet multiplet = peakDim.getMultiplet();
                }
            }
            peakList.unLinkPeak(this);
            this.markDeleted();
            peakList.peaks().remove(this);
            peakList.reIndex();
        }
    }
}

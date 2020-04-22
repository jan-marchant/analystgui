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
        //Allow for peaks picked with fewer peakdims than peaklist size
        //Eventually needs improvement for situations where we want to add higher dim peak
        //from lower dim. E.g. 2D - 3D NOESY.
        super(peakList,peak.getPeakDims().length);
        this.initPeakDimContribs();
        peak.copyTo(this);
        for (int i = 0; i < peak.getPeakDims().length; i++) {
            this.getPeakDim(i).setResonance(peak.getPeakDim(i).getResonance());
            //TODO: Suggest to bruce this would be better in setResonance (only called in NMRStarReader I think)
            peak.getPeakDim(i).getResonance().add(this.getPeakDim(i));
        }
    }

    @Override
    public void setStatus(int status) {
        super.setStatus(status);
        if (status < 0) {
            for (LabelDataset ld : LabelDataset.labelDatasetTable) {
                ld.getManagedList().deleteMatchingPeaks(this);
                ld.getManagedList().reNumber();
            }
            LabelDataset.getMasterList().deleteMatchingPeaks(this);
            LabelDataset.getMasterList().reNumber();
            //Now delete the peak
            for (PeakDim peakDim : this.peakDims) {
                peakDim.remove();
                if (peakDim.hasMultiplet()) {
                    Multiplet multiplet = peakDim.getMultiplet();
                }
            }
            peakList.unLinkPeak(this);
            this.markDeleted();
            peakList.peaks().remove(this);

            peakList.reNumber();
            //attempt to update
            FXMLController.getActiveController().charts.stream().forEach(chart -> {
                chart.clearPeakPaths();
                chart.refresh();
            });
        }
    }
}

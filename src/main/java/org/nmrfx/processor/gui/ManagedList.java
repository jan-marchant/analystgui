package org.nmrfx.processor.gui;

import org.nmrfx.processor.datasets.peaks.Peak;
import org.nmrfx.processor.datasets.peaks.PeakDim;
import org.nmrfx.processor.datasets.peaks.PeakList;

import java.util.List;

public class ManagedList extends PeakList {
    public ManagedList(LabelDataset labelDataset) {
        super(labelDataset.getManagedListName(),labelDataset.getDataset().getNDim());
    }
    public ManagedList(LabelDataset labelDataset,PeakList peakList) {
        super(labelDataset.getManagedListName()+"temp",labelDataset.getDataset().getNDim());
        peakList.copy(labelDataset.getManagedListName()+"temp",true,true,true,false);
        //might not be necessary as next line should crunch
        peakList.remove();
        this.setName(labelDataset.getManagedListName());
    }
    @Override
    public Peak getPeakByID(int idNum) throws IllegalArgumentException {
        System.out.println("Did it in ManagedList innit");
        return super.getPeakByID(idNum);
    }
}

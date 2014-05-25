/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tman.system.peer.tman;

import common.peer.AvailableResources;
import cyclon.system.peer.cyclon.PeerDescriptor;
import cyclon.system.peer.cyclon.ViewEntry;
import java.util.Comparator;

/**
 *
 * @author babbarshaer
 */
public class ComparatorByViewEntry implements Comparator<ViewEntry>{

    
    private final GradientEnum gradientEnum;
    private final int myCpuResource;
    private final int myMemoryResource;
    
    public ComparatorByViewEntry(GradientEnum gradientEnum,int myCpuResource , int myMemoryResource) {
        this.gradientEnum = gradientEnum;
        this.myCpuResource = myCpuResource;
        this.myMemoryResource = myMemoryResource;
    }
    
    
    @Override
    public int compare(ViewEntry o1, ViewEntry o2) {
        
        PeerDescriptor pd1 = o1.getDescriptor();
        PeerDescriptor pd2 = o2.getDescriptor();
        
        int resourceToCompare1 =0;
        int resourceToCompare2=0;
        
        if(gradientEnum == GradientEnum.CPU){
            
            resourceToCompare1 = pd1.getFreeCpu();
            resourceToCompare2 = pd2.getFreeCpu();
            
            if(resourceToCompare1 > myCpuResource && myCpuResource  > resourceToCompare2)
                return -1;
            else if(resourceToCompare2 > myCpuResource && myCpuResource > resourceToCompare1)
                return 1;
            else if (Math.abs(resourceToCompare1 -myCpuResource) < Math.abs(resourceToCompare2 - myCpuResource))
                return -1;
            else if (Math.abs(resourceToCompare2 - myCpuResource) < Math.abs(resourceToCompare1 - myCpuResource))
                return 1;
            else
                return 0;
        }
        
        else if( gradientEnum == GradientEnum.MEMORY){
            
            resourceToCompare1 = pd1.getFreeMemory();
            resourceToCompare2 = pd2.getFreeMemory();
            
            if(resourceToCompare1 > myMemoryResource && myMemoryResource > resourceToCompare2)
                return -1;
            else if(resourceToCompare2 > myMemoryResource && myMemoryResource > resourceToCompare1)
                return 1;
            else if (Math.abs(resourceToCompare1 - myMemoryResource) < Math.abs(resourceToCompare2 -myMemoryResource))
                return -1;
            else if (Math.abs(resourceToCompare2 - myMemoryResource) < Math.abs(resourceToCompare1 - myMemoryResource))
                return 1;
            else
                return 0;
        }
        
        
        return 0;
    }
    
}

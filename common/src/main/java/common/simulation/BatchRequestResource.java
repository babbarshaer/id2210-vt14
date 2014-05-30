package common.simulation;

import se.sics.kompics.Event;

/**
 * Indication event for the resource manager requesting for the specified resources.
 * 
 */
public final class BatchRequestResource {
    
    private final long batchRequestId;
    private final long withinBatchRequestId;
    private final long freeMemory;
    private final long freeCpu;
    private final long timeToHoldResource;

    public BatchRequestResource(long id, long numCpus, long memoryInMbs, long timeToHoldResource, long withinBatchRequestId) {
        
        this.freeCpu = numCpus;
        this.freeMemory = memoryInMbs;
        this.timeToHoldResource  = timeToHoldResource;
        this.withinBatchRequestId = withinBatchRequestId;
        this.batchRequestId = id;
    }

    

    @Override
    public boolean equals(Object obj) {
        
        if(obj instanceof BatchRequestResource){
            BatchRequestResource other = (BatchRequestResource)obj;
            if(this.batchRequestId == other.batchRequestId && this.withinBatchRequestId == other.withinBatchRequestId){
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + (int) (this.batchRequestId ^ (this.batchRequestId >>> 32));
        hash = 37 * hash + (int) (this.withinBatchRequestId ^ (this.withinBatchRequestId >>> 32));
        return hash;
    }

    public long getBatchRequestId(){
        return this.batchRequestId;
    }
    
    public long getTimeToHoldResource(){
        return this.timeToHoldResource;
    }
    
    public long getFreeCpu(){
        return this.freeCpu;
    }
    
    public long getFreeMemory(){
        return this.freeMemory;
    }
    
    public long getWithInBatchRequestId(){
        return this.withinBatchRequestId;
    }
    
}

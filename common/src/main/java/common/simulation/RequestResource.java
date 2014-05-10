package common.simulation;

import se.sics.kompics.Event;

/**
 * Indication event for the resource manager requesting for the specified resources.
 * 
 */
public final class RequestResource extends Event {
    
    private final long id;
    private final int numCpus;
    private final int memoryInMbs;
    private final int timeToHoldResource;

    public RequestResource(long id, int numCpus, int memoryInMbs, int timeToHoldResource) {
        this.id = id;
        this.numCpus = numCpus;
        this.memoryInMbs = memoryInMbs;
        this.timeToHoldResource = timeToHoldResource;
    }

    public long getId() {
        return id;
    }

    public int getTimeToHoldResource() {
        return timeToHoldResource;
    }

    public int getMemoryInMbs() {
        return memoryInMbs;
    }

    public int getNumCpus() {
        return numCpus;
    }

    @Override
    public boolean equals(Object obj) {
        
        if(obj instanceof RequestResource){
            RequestResource other = (RequestResource)obj;
            if(this.id == other.id){
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + (int) (this.id ^ (this.id >>> 32));
        return hash;
    }
    
}

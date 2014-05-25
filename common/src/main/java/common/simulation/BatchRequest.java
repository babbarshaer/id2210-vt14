/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package common.simulation;

import se.sics.kompics.Event;

/**
 *
 * @author babbarshaer
 */
public class BatchRequest extends Event{
    
    private final long batchRequestId;
    private final long numberOfMachines;
    private final long freeMemory;
    private final long freeCpu;
    private final long timeToHoldResource;
    
    
    public BatchRequest(long batchRequestId, long numberOfMachines, long freeCpu, long freeMemory, long timeToHoldResource){
        this.batchRequestId  = batchRequestId;
        this.numberOfMachines = numberOfMachines;
        this.freeMemory = freeMemory;
        this.freeCpu = freeCpu;
        this.timeToHoldResource = timeToHoldResource;
    }

    /**
     * @return the batchRequestId
     */
    public long getBatchRequestId() {
        return batchRequestId;
    }

    /**
     * @return the numberOfMachines
     */
    public long getNumberOfMachines() {
        return numberOfMachines;
    }

    /**
     * @return the memory
     */
    public long getFreeMemory() {
        return freeMemory;
    }

    /**
     * @return the cpu
     */
    public long getFreeCpu() {
        return freeCpu;
    }
    
    public long getTimeToHoldResource(){
        return timeToHoldResource;
    }
    
    
}

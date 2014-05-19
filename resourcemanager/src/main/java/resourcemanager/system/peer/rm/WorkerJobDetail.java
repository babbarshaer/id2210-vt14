/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import java.util.List;
import java.util.UUID;
import se.sics.kompics.address.Address;

/**
 * Represents the Job submitted to a worker by a Peer.
 * @author babbarshaer
 */
public class WorkerJobDetail {
    
    private final int cpu;
    private final int memory;
    private final long requestId;
    private final int timeToHoldResource;
    private final Address schedulerAddress;
    private JobStatusEnum jobStatus;
    private final List<Address> workers;
    private final UUID resourceRequestUUID;
    
    
    public WorkerJobDetail(int cpu, int memory, long requestId, int timeToHoldResource, Address schedulerAddress , List<Address> workers, UUID resourceRequestUUID){
        this.cpu = cpu;
        this.memory = memory;
        this.requestId = requestId;
        this.timeToHoldResource = timeToHoldResource;
        this.schedulerAddress = schedulerAddress;
        this.jobStatus = JobStatusEnum.QUEUED;
        this.workers = workers;
        this.resourceRequestUUID = resourceRequestUUID;
    }

    /**
     * @return the cpu
     */
    public int getCpu() {
        return cpu;
    }

    /**
     * @return the memory
     */
    public int getMemory() {
        return memory;
    }

    /**
     * @return the requestId
     */
    public long getRequestId() {
        return requestId;
    }

    /**
     * @return the timeToHoldResource
     */
    public int getTimeToHoldResource() {
        return timeToHoldResource;
    }

    /**
     * @return the schedulerAddress
     */
    public Address getSchedulerAddress() {
        return schedulerAddress;
    }
    
    /**
     * 
     * @return the jobStatus
     */
    public JobStatusEnum getJobStatus(){
        return this.jobStatus;
    }
    
    /**
     * Update the job status.
     * @param jobStatus 
     */
    public void setJobStatus(JobStatusEnum jobStatus){
        this.jobStatus = jobStatus;
    }
    
    public UUID getResourceRequestUUID(){
        return this.resourceRequestUUID;
    }
    
    
     @Override
    public boolean equals(Object obj) {
        if (obj instanceof WorkerJobDetail) {

            WorkerJobDetail otherJobDetail = (WorkerJobDetail) obj;
            if (this.requestId == otherJobDetail.requestId) {
                return true;
            }
        }
        return false;
    }

    
    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + (int) (this.requestId ^ (this.requestId >>> 32));
        return hash;
    }
    
    public  List<Address> getWorkers(){
        return this.workers;
    }
}

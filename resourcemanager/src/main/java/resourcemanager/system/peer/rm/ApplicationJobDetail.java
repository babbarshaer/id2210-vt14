/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package resourcemanager.system.peer.rm;

import common.simulation.RequestResource;

/**
 * Holder of the peer resource request details.
 *
 * @author babbarshaer
 */
public class ApplicationJobDetail {

    private final int cpu;
    private final int memory;
    private final long requestId;
    private final int timeToHoldResource;
    private JobStatusEnum jobStatus;

    public ApplicationJobDetail(int cpu, int memory, long requestId, int timeToHoldRequest) {

        this.cpu = cpu;
        this.memory = memory;
        this.requestId = requestId;
        this.timeToHoldResource = timeToHoldRequest;
        this.jobStatus = JobStatusEnum.REQUESTED;

    }

    // Convenience Constructor.
    public ApplicationJobDetail(RequestResource requestResource) {

        this.cpu = requestResource.getNumCpus();
        this.memory = requestResource.getMemoryInMbs();
        this.requestId = requestResource.getId();
        this.timeToHoldResource = requestResource.getTimeToHoldResource();
        this.jobStatus = JobStatusEnum.REQUESTED;
    }

    public ApplicationJobDetail(long requestId) {
        this.cpu = -1;
        this.memory = -1;
        this.requestId = requestId;
        this.timeToHoldResource = -1;
        this.jobStatus = null;
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
     * @return the jobStatus
     */
    public JobStatusEnum getJobStatus() {
        return this.jobStatus;
    }

    /**
     * Set the value of the Job Status.
     *
     * @param jobStatus
     */
    public void setJobStatus(JobStatusEnum jobStatus) {
        this.jobStatus = jobStatus;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ApplicationJobDetail) {

            ApplicationJobDetail otherJobDetail = (ApplicationJobDetail) obj;
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

    @Override
    public String toString() {
        String printString = "Cpu: " + cpu + " Memory: " + memory + " RequestId: " + requestId + " Time To Hold Resource: " + timeToHoldResource;
        return printString;
    }

}

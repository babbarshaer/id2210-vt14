/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import common.simulation.BatchRequestResource;
import common.simulation.RequestResource;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Timeout For checking if the request has timed out.
 * @author babbarshaer
 */
public class BatchResourceRequestTimeout extends Timeout{
    
    private final BatchRequestResource resourceRequest;
    
    
    public BatchResourceRequestTimeout(SchedulePeriodicTimeout request, BatchRequestResource resourceRequest){
        super(request);
        this.resourceRequest = resourceRequest;
    }
    
    public BatchResourceRequestTimeout(ScheduleTimeout request, BatchRequestResource resourceRequest){
        super(request);
        this.resourceRequest = resourceRequest;
    }
    
    public BatchRequestResource getBatchResourceRequest(){
        return this.resourceRequest;
    }
    
}

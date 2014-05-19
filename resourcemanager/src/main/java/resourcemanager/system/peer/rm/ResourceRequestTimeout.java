/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import common.simulation.RequestResource;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Timeout For checking if the request has timed out.
 * @author babbarshaer
 */
public class ResourceRequestTimeout extends Timeout{
    
    private final RequestResource resourceRequest;
    
    
    public ResourceRequestTimeout(SchedulePeriodicTimeout request, RequestResource resourceRequest){
        super(request);
        this.resourceRequest = resourceRequest;
    }
    
    public ResourceRequestTimeout(ScheduleTimeout request, RequestResource resourceRequest){
        super(request);
        this.resourceRequest = resourceRequest;
    }
    
    public RequestResource getResourceRequest(){
        return this.resourceRequest;
    }
    
}

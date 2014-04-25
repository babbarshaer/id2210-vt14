/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 * Schedule a timeout based on the job processing time.
 * @author babbarshaer
 */
public class JobCompletionTimeout extends Timeout{
    
    private final RequestResources.Request resourceRequest;
    private final PeerJobDetail peerJobDetail;
    
    public JobCompletionTimeout(ScheduleTimeout st , RequestResources.Request resourceRequest){
        super(st);
        this.resourceRequest = resourceRequest;
        this.peerJobDetail = null;
    }
    
    public JobCompletionTimeout(ScheduleTimeout st , PeerJobDetail peerJobdetail){
        super(st);
        this.peerJobDetail  = peerJobdetail;
        this.resourceRequest = null;
    }
    
    
    public RequestResources.Request getResourceRequest(){
        return this.resourceRequest;
    }
    
    public PeerJobDetail getPeerJobDetail (){
        return this.peerJobDetail;
    }
    
    
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import se.sics.kompics.Event;

/**
 *  Triggered by the scheduler when the scheduling of the request is completed.
 * @author babbarshaer
 */
public class RequestCompletion extends Event{

    long requestId;
    long timeTaken;
    
    public RequestCompletion(long requestId , long timeTaken){
        this.requestId = requestId;
        this.timeTaken = timeTaken;
    }
    
    public RequestCompletion(long requestId){
        this.requestId = requestId;
        this.timeTaken = 0;
    }
    
    public Long getId() {
        return requestId;
    }
    public Long getTimeTaken(){
        return this.timeTaken;
    }
    
}

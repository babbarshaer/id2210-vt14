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
    
    public RequestCompletion(long requestId){
        this.requestId = requestId;
    }
    
    public Long getId() {
        return requestId;
    }
    
}

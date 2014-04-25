/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 * Sent by the requestor to the worker informing about the processing decision.
 * 
 * @author babbarshaer
 */
public class ProcessRequestResponse extends Message{
    
    private static final long serialVersionUID = -3098550239865688299L;
    private final long requestId;
    private final boolean status;
    
    public ProcessRequestResponse(Address source, Address destination, long requestId , boolean status){
        super(source, destination);
        this.requestId = requestId;
        this.status = status;
    }
    
    public long getRequestId(){
        return this.requestId;
    }
    
    public boolean getStatus(){
        return this.status;
    }
    
}

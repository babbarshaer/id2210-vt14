/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package resourcemanager.system.peer.rm;

import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author babbarshaer
 */
public class CancelJob extends Message {

    private static final long serialVersionUID = 1L;
    private final long requestId;
    
    
    public CancelJob(Address source, Address destination , long requestId) {
        super(source, destination);
        this.requestId = requestId;
    }

    /**
     * 
     * @return the requestId.
     */
    public long getRequestId(){
        return this.requestId;
    }
    
}

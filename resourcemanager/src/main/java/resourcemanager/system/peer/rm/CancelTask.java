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
public class CancelTask extends Message {

    private static final long serialVersionUID = 1L;
    private final long requestId;
    private final boolean isBatchRequest;
    private final long withinbatchRequestId;
    
    
    public CancelTask(Address source, Address destination , long requestId) {
        super(source, destination);
        this.requestId = requestId;
        this.isBatchRequest  = false;
        this.withinbatchRequestId  = -1;
    }
    
    public CancelTask(Address source, Address destination , long requestId, boolean isBatchRequest , long withinBatchRequestId){
        super(source, destination);
        this.requestId = requestId;
        this.isBatchRequest  = isBatchRequest;
        this.withinbatchRequestId  = withinBatchRequestId;
    }
    

    /**
     * 
     * @return the requestId.
     */
    public long getRequestId(){
        return this.requestId;
    }
    
    /**
     * 
     * @return isBatchRequestId.
     */
    public boolean isBatchRequest(){
        return this.isBatchRequest;
    }
    
    /**
     * 
     * @return withinBatchRequestId.
     */
    public long withinBatchRequestId(){
        return this.withinbatchRequestId;
    }
    
    
}

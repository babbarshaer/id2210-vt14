/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package resourcemanager.system.peer.rm;

import common.simulation.RequestResource;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author babbarshaer
 */
public class RescheduleJob extends Message {

    private static final long serialVersionUID = -6918099718727366469L;

    private final RequestResource resourceRequest;
    private int TTL;

    public RescheduleJob(Address source,  Address destination,  RequestResource resourceRequest) {
        super(source,destination);
        this.resourceRequest = resourceRequest;
        this.TTL = 4;
    }
    
    // Specified TTL 
     public RescheduleJob(Address source,  Address destination,  RequestResource resourceRequest , int TTL) {
        super(source,destination);
        this.resourceRequest = resourceRequest;
        this.TTL = TTL;
    }

    public RequestResource getResourceRequest() {
        return this.resourceRequest;
    }
    
    public int getTTL(){
        return this.TTL;
    }
    
    // Reduce the TTL on every re routing.
    public void reduceTTL(){
        TTL -=1;
    }
    
}

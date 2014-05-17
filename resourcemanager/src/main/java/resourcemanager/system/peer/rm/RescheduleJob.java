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
    private ResourceEnum dominantResource;
    private int dominantResourceRetries;

    public RescheduleJob(Address source,  Address destination,  RequestResource resourceRequest) {
        super(source,destination);
        this.resourceRequest = resourceRequest;
        this.TTL = 4;
        this.dominantResourceRetries = 2;
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
    
    
    public ResourceEnum getDominantResource(){
        return this.dominantResource;
    }
    
    public int getDominantResourceRetries(){
        return this.dominantResourceRetries;
    }

    @Override
    public boolean equals(Object obj) {
        
        if(obj instanceof RescheduleJob){
            RescheduleJob other = (RescheduleJob)obj;
            if(resourceRequest.getId() == other.resourceRequest.getId()){
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
       return resourceRequest.hashCode();
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package resourcemanager.system.peer.rm;

import common.simulation.RequestResource;
import java.util.UUID;
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
    private final UUID resourceRequestUUID;

    public RescheduleJob(Address source,  Address destination,  RequestResource resourceRequest, UUID resourceRequestUUID) {
        super(source,destination);
        this.resourceRequest = resourceRequest;
        this.TTL = 3;
        this.dominantResourceRetries = 2;
        this.resourceRequestUUID = resourceRequestUUID;
    }
    
    // Specified TTL 
     public RescheduleJob(Address source,  Address destination,  RequestResource resourceRequest , int TTL , UUID resourceRequestUUID) {
        super(source,destination);
        this.resourceRequest = resourceRequest;
        this.TTL = TTL;
        this.resourceRequestUUID  = resourceRequestUUID;
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
    
    public UUID getResourceRequestUUID(){
        return this.resourceRequestUUID;
    }
    
    public void setDestinationAddress(Address address){
        setDestination(address);
    }
    
    public void resetTTL(){
        this.TTL = 3;
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

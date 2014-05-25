/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import se.sics.kompics.address.Address;

/**
 *
 * @author babbarshaer
 */
public class BatchRequestWorkerInformation {
    
    
    private Address myAddress;
    private int queueLength;
    
    public BatchRequestWorkerInformation(Address workerAddress , int queueLength){
        this.myAddress  = workerAddress;
        this.queueLength = queueLength;
    }
    
    public int getQueueLength(){
        return this.queueLength;
    }
    
    public Address getAddress(){
        return this.myAddress;
    }

    @Override
    public boolean equals(Object obj) {
        
        if(obj instanceof BatchRequestWorkerInformation){
            BatchRequestWorkerInformation other = (BatchRequestWorkerInformation)obj;
            return other.myAddress.equals(this.myAddress);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + (this.myAddress != null ? this.myAddress.hashCode() : 0);
        return hash;
    }
    
    
    
}

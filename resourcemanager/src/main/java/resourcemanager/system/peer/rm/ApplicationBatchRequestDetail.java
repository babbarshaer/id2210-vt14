/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import common.simulation.BatchRequest;
import java.util.List;

/**
 *
 * @author babbarshaer
 */
public class ApplicationBatchRequestDetail {
        
    private final BatchRequest batchRequest;
    
    private int numberOfProbesReply;
    private int numberOfProbesSent;
    
    public ApplicationBatchRequestDetail(BatchRequest request){
        this.batchRequest = request;
       numberOfProbesReply = 0;
    }
    
    public BatchRequest getBatchRequest(){
        return this.batchRequest;
    }
    
    public int getNumberOfProbesReply(){
        return this.numberOfProbesReply;
    }
    
    public void incrementProbesReply(){
        numberOfProbesReply+=1;
    }
    
    
    public int getNumberOfProbesSent(){
        return this.numberOfProbesSent;
    }
    
    public void setNumberOfProbesSent(int numberOfProbesSent){
        this.numberOfProbesSent = numberOfProbesSent;
    }
    
    
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof ApplicationBatchRequestDetail){
            
            ApplicationBatchRequestDetail other  = (ApplicationBatchRequestDetail)obj;
            if(this.batchRequest.getBatchRequestId() == other.getBatchRequest().getBatchRequestId()){
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + (this.batchRequest != null ? this.batchRequest.hashCode() : 0);
        return hash;
    }
    
    
    
    
}

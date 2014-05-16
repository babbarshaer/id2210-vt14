/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package common.simulation;

import se.sics.kompics.Event;

/**
 *
 * @author babbarshaer
 */
public class BootstrapUtilizationHandler extends Event{
    
    private final long requestsToBeScheduled;
    
    public BootstrapUtilizationHandler(long requestsToBeScheduled){
        this.requestsToBeScheduled = requestsToBeScheduled;
    }
    
    public long getRequestsToBeScheduled(){
        return this.requestsToBeScheduled;
    }
    
}

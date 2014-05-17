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
 * Message to inform the scheduler about removing the job rescheduled from the list.
 * 
 * @author babbarshaer
 */
public class RemoveRescheduleJob extends Message{
    
    private final RequestResource event;
    
    public RemoveRescheduleJob(Address source, Address destination , RequestResource event){
        super(source, destination);
        this.event   = event;
    }
    
    public RequestResource getRequestEvent(){
        return this.event;
    }
            
    
}

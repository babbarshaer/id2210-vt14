/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package resourcemanager.system.peer.rm;

import java.util.UUID;
import se.sics.kompics.address.Address;
import se.sics.kompics.network.Message;

/**
 *
 * @author babbarshaer
 */
public class JobCompletionEvent extends Message{
    
    private static final long serialVersionUID = 1L;
    private final long requestId;
    private final UUID resourceRequestUUID;
    
    public JobCompletionEvent(Address source, Address destination, long requestId, UUID resourceRequestUUID){
        super(source, destination);
        this.requestId = requestId;
        this.resourceRequestUUID = resourceRequestUUID;
    }
    
   /**
    * @return request id.
    */
    public long getRequestId(){
        return this.requestId;
    }
    
    /**
     * Get Resource Request UUID.
     * @return UUID
     */
    public UUID getResourceRequestUUID(){
        return this.resourceRequestUUID;
    }
}

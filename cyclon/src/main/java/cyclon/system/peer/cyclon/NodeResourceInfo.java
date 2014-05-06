/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package cyclon.system.peer.cyclon;

import se.sics.kompics.address.Address;

/**
 * 
 * Free Resource Information of a node.
 * @author babbarshaer
 */
public class NodeResourceInfo {
    
    private final int freeCpu;
    private final int freeMemory;
    private final Address node;
    
    public NodeResourceInfo( Address nodeAddress, int freeCpu , int freeMemory){
        
        this.node = nodeAddress;
        this.freeCpu = freeCpu;
        this.freeMemory = freeMemory;
        
    }

    /**
     * @return the freeCpu
     */
    public int getFreeCpu() {
        return freeCpu;
    }

    /**
     * @return the freeMemory
     */
    public int getFreeMemory() {
        return freeMemory;
    }
    
    /**
     * 
     * @return Node Address Information.
     */
    public Address getNodeAddress(){
        return this.node;
    }
    
    //FIXME: Ovveride the equals and the hashcode method.
    
}

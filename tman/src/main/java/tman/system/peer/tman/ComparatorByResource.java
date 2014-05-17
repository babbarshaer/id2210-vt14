/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tman.system.peer.tman;

import java.util.Comparator;

/**
 *
 * @author babbarshaer
 */
public class ComparatorByResource implements Comparator<Integer>{

    private Integer resource;
    
    public ComparatorByResource(Integer resource){
        this.resource = resource;
    }
    
    @Override
    public int compare(Integer o1, Integer o2) {
        
        assert(o1 == o2);
        if( o1 > resource && o2 <resource)
            return -1;        
        else if( o2 > resource && o1 < resource)
            return 1;
        else if(Math.abs(resource-o1) < Math.abs(resource-o2))
            return -1;
        
        return 1;
        
    }
    
    
    
    
}

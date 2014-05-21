/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tman.system.peer.tman;

import common.peer.AvailableResources;
import cyclon.system.peer.cyclon.PeerDescriptor;
import cyclon.system.peer.cyclon.ViewEntry;
import java.util.ArrayList;
import java.util.List;
import se.sics.kompics.address.Address;

/**
 *
 * @author babbarshaer
 */
public class GradientCacheTest {
    
    
    public static void main(String[] args) {
        
        AvailableResources ar1 = new AvailableResources(4, 1000);
       GradientCache cache = new GradientCache(0, null, ar1, 0, null, GradientEnum.CPU);
        
        Address ad1 = new Address(null, 0, 1);
        PeerDescriptor pd1  = new PeerDescriptor(ad1, 6 , 8000);
        ViewEntry entry1 = new ViewEntry(pd1);
        
        Address ad2 = new Address(null, 0, 2);
        PeerDescriptor pd2  = new PeerDescriptor(ad1, 5 , 8000);
        ViewEntry entry2 = new ViewEntry(pd2);
        
        ArrayList<ViewEntry> entriesToBeSupplied = new ArrayList<ViewEntry>();
        entriesToBeSupplied.add(entry1);
        entriesToBeSupplied.add(entry2);
        
        
        cache.setEntry(entriesToBeSupplied);
        cache.arrangeNodesInPreferenceOrder();
        
        List<ViewEntry> sortedEntries = cache.getEntries();
        for(ViewEntry entry : sortedEntries){
            System.out.println("Cpu Entry : " + entry.getDescriptor().getFreeCpu());
        }
        
    }
    
}

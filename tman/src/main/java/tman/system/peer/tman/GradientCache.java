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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import se.sics.kompics.address.Address;

/**
 *
 * @author babbarshaer
 */
public class GradientCache {

    private Comparator<ViewEntry> comparatorByAge = new Comparator<ViewEntry>() {
        public int compare(ViewEntry o1, ViewEntry o2) {
            if (o1.getDescriptor().getAge() > o2.getDescriptor().getAge()) {
                return 1;
            } else if (o1.getDescriptor().getAge() < o2.getDescriptor().getAge()) {
                return -1;
            } else {
                return 0;
            }
        }
    };

    private final int size;
    private final Address self;
    private ArrayList<ViewEntry> entries;
    private HashMap<Address, ViewEntry> d2e;
    private Random random = new Random(10);
    
    //FIXME: Incorporate the SoftMax Approach in the same
    private AvailableResources availableResources;
    private double temperature;
    private Random r;
    private GradientEnum gradientEnum;

    public GradientCache(int size, Address self, AvailableResources availableResources , double temperature , Random r, GradientEnum gradientEnum) {
        super();
        this.self = self;
        this.size = size;
        this.entries = new ArrayList<ViewEntry>();
        this.d2e = new HashMap<Address, ViewEntry>();
        this.availableResources = availableResources;
        this.temperature = temperature;
        this.r = r;
        this.gradientEnum = gradientEnum;
    }

    public void incrementDescriptorAges() {
        for (ViewEntry entry : entries) {
            entry.getDescriptor().incrementAndGetAge();
        }
    }

    /**
     * For now lets go with the oldest entry to exchange the data with.
     *
     * @return
     */
    public Address selectPeerToShuffleWith() {
        if (entries.isEmpty()) {
            return null;
        }
        ViewEntry oldestEntry = Collections.max(entries, comparatorByAge);
//        removeEntry(oldestEntry);
        return oldestEntry.getDescriptor().getAddress();
    }

    /**
     * List of the peer descriptors to be exchanged with the neighbor.
     *
     * @param count
     * @param destinationPeer
     * @return
     */
    public ArrayList<PeerDescriptor> selectToSendAtActive(int count, Address destinationPeer) {
        ArrayList<ViewEntry> randomEntries = generateRandomSample(count);

        ArrayList<PeerDescriptor> descriptors = new ArrayList<PeerDescriptor>();
        for (ViewEntry cacheEntry : randomEntries) {
            cacheEntry.sentTo(destinationPeer);
            descriptors.add(cacheEntry.getDescriptor());
        }
        return descriptors;
    }

    /**
     * Called when replying to the peer with the buffer.
     *
     * @param count
     * @param destinationPeer
     * @return
     */
    public ArrayList<PeerDescriptor> selectToSendAtPassive(int count, Address destinationPeer) {
        ArrayList<ViewEntry> randomEntries = generateRandomSample(count);
        ArrayList<PeerDescriptor> descriptors = new ArrayList<PeerDescriptor>();

        for (ViewEntry cacheEntry : randomEntries) {
            cacheEntry.sentTo(destinationPeer);
            descriptors.add(cacheEntry.getDescriptor());
        }

        return descriptors;
    }

    /**
     * Peer retention strategy in the view for a base node.
     *
     * @param from
     * @param descriptors
     */
    public void selectToKeep(ArrayList<PeerDescriptor> descriptors) {

        for (PeerDescriptor descriptor : descriptors) {
            if (self.equals(descriptor.getAddress())) {
                // do not keep descriptor of self
                continue;
            }

            // Keep the freshest peers.
            if (d2e.containsKey(descriptor.getAddress())) {

                ViewEntry entry = d2e.get(descriptor.getAddress());
                if (entry.getDescriptor().getAge() > descriptor.getAge()) {
                    // we keep the lowest age descriptor
                    removeEntry(entry);
                    addEntry(new ViewEntry(descriptor));
                    continue;
                } else {
                    continue;
                }
            }
            // add a new entry.
            addEntry(new ViewEntry(descriptor));
        }

        // Now once the entries are merged, sort them based on the utility.
        arrangeNodesInPreferenceOrder();
        //Check for any king of discrepancy.
        checkSize();
        //Now remove the entries based on the top ranking selection policy.
        removeExcessEntries();
        //Again check the size.
        checkSize();
    }

    /**
     * Bring size of the view back to the constant value.
     */
    private void removeExcessEntries() {
       
        if (entries.size() <=  size) {
            return;
        }
        
        int listSize = entries.size();       
        while(listSize > size){
            removeEntry(entries.get(listSize-1));
            listSize -=1;
        }
    }

    /**
     * Simply return all the peer descriptors present in the view of the base node.
     * @return 
     */
    public final ArrayList<PeerDescriptor> getAll() {
        ArrayList<PeerDescriptor> descriptors = new ArrayList<PeerDescriptor>();

        for (ViewEntry cacheEntry : entries) {
            descriptors.add(cacheEntry.getDescriptor());
        }

        return descriptors;
    }

    
    public final List<Address> getRandomPeers(int count) {
        ArrayList<ViewEntry> randomEntries = generateRandomSample(count);
        LinkedList<Address> randomPeers = new LinkedList<Address>();

        for (ViewEntry cacheEntry : randomEntries) {
            randomPeers.add(cacheEntry.getDescriptor().getAddress());
        }

        return randomPeers;
    }

    /**
     * Randomly select the entries from the base node view.
     * @param n
     * @return 
     */
    private ArrayList<ViewEntry> generateRandomSample(int n) {
        ArrayList<ViewEntry> randomEntries;
        if (n >= entries.size()) {
            // return all entries
            randomEntries = new ArrayList<ViewEntry>(entries);
        } else {
            // return count random entries
            randomEntries = new ArrayList<ViewEntry>();
            // Don Knuth, The Art of Computer Programming, Algorithm S(3.4.2)
            int t = 0, m = 0, N = entries.size();
            while (m < n) {
                int x = random.nextInt(N - t);
                if (x < n - m) {
                    randomEntries.add(entries.get(t));
                    m += 1;
                    t += 1;
                } else {
                    t += 1;
                }
            }
        }
        return randomEntries;
    }

    
    private void addEntry(ViewEntry entry) {
        entries.add(entry);
        d2e.put(entry.getDescriptor().getAddress(), entry);
        checkSize();
    }

    
    private void removeEntry(ViewEntry entry) {
        entries.remove(entry);
        d2e.remove(entry.getDescriptor().getAddress());
        checkSize();
    }

    
    private void checkSize() {
        if (entries.size() != d2e.size()) {
            throw new RuntimeException("WHD " + entries.size() + " <> " + d2e.size());
        }
    }

    
    /**
     * Sort the nodes in the decreasing order of there utility. Performed after
     * merging.
     *
     * @param partnersDescriptor
     */
    private void arrangeNodesInPreferenceOrder() {

        //For now lets just choose free cpu's as the ordering mechanism.
        //FIXME: Add check based on which the gradient that is required to be built.
        //Step1: Start with the sorting of the nodes. Using insertion sort.
        for (int j = 1; j < entries.size(); j++) {

            int i = j - 1;
            ViewEntry partnerEntry = entries.get(j);

            while (i >= 0 && isDecreasingOrder(partnerEntry.getDescriptor(), entries.get(i).getDescriptor())) {

                // Found a node to exchange the values.
                entries.remove(i + 1);
                entries.add(i + 1, entries.get(i));

                i -= 1;
            }
            entries.remove(i + 1);
            entries.add(i + 1, partnerEntry);            //Add the node in the list at the appropriate position.
        }

    }

    
    /**
     * Simply check if nodeResourceInfo1 is better suited to be neighbor of base
     * node than nodeResourceInfo2.
     *
     * @param nodeResourceInfo1
     * @param nodeResourceInfo2
     * @return
     */
    private boolean isDecreasingOrder(PeerDescriptor partnerDescriptor1, PeerDescriptor partnerDescriptor2) {
        
        int baseNodeUtility = 0;
        int nodeUtility1 = 0;
        int nodeUtility2 =0 ;
        
        if(gradientEnum == GradientEnum.CPU){
            baseNodeUtility = availableResources.getNumFreeCpus();
            nodeUtility1 = partnerDescriptor1.getFreeCpu();
            nodeUtility2 = partnerDescriptor2.getFreeCpu();
        }
        
        else if(gradientEnum  == GradientEnum.MEMORY){
            baseNodeUtility = availableResources.getFreeMemInMbs();
            nodeUtility1 = partnerDescriptor1.getFreeMemory();
            nodeUtility2 = partnerDescriptor2.getFreeMemory();
        }
        
        return basicUtilityPreferenceOrder(baseNodeUtility, nodeUtility1, nodeUtility2);
    }

/**
 *  Apply Simple Utility Preference Order on the supplied values.
 *  FIXME: Come Up with a combined Utility Preference Order.
 * @param baseNodeUtility
 * @param nodeUtility1
 * @param nodeUtility2 
 */
    
    private boolean basicUtilityPreferenceOrder(int baseNodeUtility , int nodeUtility1 , int nodeUtility2){        
        if((nodeUtility1 > baseNodeUtility && baseNodeUtility > nodeUtility2)  || (Math.abs(nodeUtility1-baseNodeUtility) < Math.abs(nodeUtility2 - baseNodeUtility)))
                return true;
        return false;
    }
    
     /**
     * Select a random neighbor from the list.
     * @return 
     */
    public Address getSoftMaxAddressForGradient() {
        //Collections.sort(entries, new ComparatorById(self));  // No sorting required as already sorted.
        
        double rnd = r.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val / temperature);
            total += values[i];
        }

        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                values[i] += values[i - 1];
            }
            // normalise the probability for this entry
            double normalisedUtility = values[i] / total;
            if (normalisedUtility >= rnd) {
                return entries.get(i).getDescriptor().getAddress();
            }
        }
        return entries.isEmpty() ? null : entries.get(entries.size() - 1).getDescriptor().getAddress();
    }

}

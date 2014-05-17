/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 *
 * @author babbarshaer
 */
public class ComparatorTestClass {

    private static Random r = new Random();

    public static void main(String[] args) {

        List<Integer> dummyIntegers = new ArrayList<Integer>();
        dummyIntegers.add(2);
        dummyIntegers.add(0);
        dummyIntegers.add(4);
        dummyIntegers.add(6);
        dummyIntegers.add(8);
        dummyIntegers.add(10);
        dummyIntegers.add(8);

        Collections.sort(dummyIntegers, new ComparatorByResource(8));
        for (Integer i : dummyIntegers) {
            System.out.println("Integer Value : " + i);
        }
        
        Integer response1 = getNeighborUsingModifiedApproach(dummyIntegers);
        Integer response2 = getNeighborUsingModifiedApproach(dummyIntegers);
        Integer response3 = getNeighborUsingModifiedApproach(dummyIntegers);
        

        System.out.println("Response1: " + response1 + " Response2: " + response2 + " Response3: " + response3);
        
        
        List<Integer> integer = new ArrayList<Integer>();
        
        
        
    }

    private static Integer getSoftMaxIInteger(List<Integer> entries) {

        double rnd = r.nextDouble();
        double total = 0.0d;
        double[] values = new double[entries.size()];
        int j = entries.size() + 1;
        for (int i = 0; i < entries.size(); i++) {
            // get inverse of values - lowest have highest value.
            double val = j;
            j--;
            values[i] = Math.exp(val /  0.8);
            total += values[i];
        }

        for (int i = 0; i < values.length; i++) {
            if (i != 0) {
                values[i] += values[i - 1];
            }
            // normalise the probability for this entry
            double normalisedUtility = values[i] / total;
            if (normalisedUtility >= rnd) {
                return entries.get(i);
            }
        }
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }
    
    /**
     * Fetching the entries from the top half to determine the rescheduling.
     * @param entries
     * @return 
     */
    private static Integer getNeighborUsingModifiedApproach(List<Integer> entries){
        
        
        if(entries.isEmpty()){
            return null;
        }
        
        // Get the midpoint ...
        int half = ((entries.size()+1) /2);
        
        // Then fetch a random entry from the first half.
        int index = r.nextInt(half);
        return entries.get(index);
        
    }
    
    
}

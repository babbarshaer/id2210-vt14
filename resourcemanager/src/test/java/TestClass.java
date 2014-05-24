
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import resourcemanager.system.peer.rm.ApplicationJobDetail;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author babbarshaer
 */
public class TestClass {
    
    public static void main(String[] args) {
        
        
//        List<Integer> integerList = new ArrayList<Integer>();
//        integerList.add(1);
//        integerList.add(2);
//        integerList.add(3);
//        integerList.add(4);
//        integerList.add(5);
//        integerList.add(6);
//        
//
//        Random r  = new Random();
//        List<Integer> newList = new ArrayList<Integer>();
//        Integer i = newList.get(r.nextInt(newList.size()));
//        
//        
//        System.out.println("Integer i : " + i);
//        
//        List<Integer> newIntegerList = new ArrayList<Integer>(integerList);
//        newIntegerList.subList(2, newIntegerList.size()).clear();
//        
//                
//        for(Integer i1 : newIntegerList)
//            System.out.println("Integer: " + i1);
//        
//        
        Set<ApplicationJobDetail> scheduledJobs = new HashSet<ApplicationJobDetail>();
        ApplicationJobDetail jobDetail1 = new ApplicationJobDetail(1);
        ApplicationJobDetail jobDetail2 = new ApplicationJobDetail(1);
        scheduledJobs.add(jobDetail1);
        scheduledJobs.add(jobDetail2);
        
        System.out.println("Length Of Scheduled Jobs : " + scheduledJobs.size());
        
        scheduledJobs.remove(new ApplicationJobDetail(1));
        
        System.out.println("Length Of Scheduled Jobs : " + scheduledJobs.size());
        
        
    }
    
}

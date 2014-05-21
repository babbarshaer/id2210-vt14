
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        
        
        List<Integer> integerList = new ArrayList<Integer>();
        integerList.add(1);
        integerList.add(2);
        integerList.add(3);
        integerList.add(4);
        integerList.add(5);
        integerList.add(6);
        
        
        List<Integer> newIntegerList = new ArrayList<Integer>(integerList);
        newIntegerList.subList(2, newIntegerList.size()).clear();
        
                
        for(Integer i : newIntegerList)
            System.out.println("Integer: " + i);
        
        
    }
    
}

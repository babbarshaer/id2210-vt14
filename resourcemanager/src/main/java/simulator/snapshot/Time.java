/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package simulator.snapshot;

import se.sics.kompics.Event;

/**
 * Contains the time information regarding the various aspects of the execution.
 * @author babbarshaer
 */
public class Time extends Event{
    
    private final long totalTime;
    private final long ninetyNinthTime;
    
    public Time(long totalTime , long ninetyNinthTime){
        this.totalTime = totalTime;
        this.ninetyNinthTime = ninetyNinthTime;
    }
    
    
    public long getTotalTime(){
        return this.totalTime;
    }
 
    public long getNinetyNinthTime(){
        return this.ninetyNinthTime;
    }
}

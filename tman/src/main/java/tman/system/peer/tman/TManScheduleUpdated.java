/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tman.system.peer.tman;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author babbarshaer
 */
public class TManScheduleUpdated extends Timeout{
    
    public TManScheduleUpdated(SchedulePeriodicTimeout request) {
            super(request);
        }

        public TManScheduleUpdated(ScheduleTimeout request) {
            super(request);
        }
    
}

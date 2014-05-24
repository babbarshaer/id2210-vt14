/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package simulator.snapshot;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;

/**
 *
 * @author babbarshaer
 */
public class UtilizationCalculationTimeout extends Timeout {

    public UtilizationCalculationTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }

    public UtilizationCalculationTimeout(ScheduleTimeout request) {
        super(request);
    }

}

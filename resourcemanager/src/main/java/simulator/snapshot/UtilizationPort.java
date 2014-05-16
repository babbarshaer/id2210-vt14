/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package simulator.snapshot;

import resourcemanager.system.peer.rm.RequestCompletion;
import se.sics.kompics.PortType;

/**
 * Utilization Port
 * @author babbarshaer
 */
public class UtilizationPort extends PortType{{
    
            negative(RequestCompletion.class);
            positive(Time.class);
    
}}

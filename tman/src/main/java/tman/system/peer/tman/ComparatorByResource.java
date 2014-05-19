/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tman.system.peer.tman;

import cyclon.system.peer.cyclon.PeerDescriptor;
import java.util.Comparator;

/**
 *
 * @author babbarshaer
 */
public class ComparatorByResource implements Comparator<PeerDescriptor> {

    GradientEnum gradientEnum;

    public ComparatorByResource(GradientEnum gradientEnum) {
        this.gradientEnum = gradientEnum;
    }

    @Override
    public int compare(PeerDescriptor o1, PeerDescriptor o2) {

        if (gradientEnum == GradientEnum.CPU) {

            if (o1.getFreeCpu() > o2.getFreeCpu()) {
                return -1;
            } else if (o2.getFreeCpu() > o1.getFreeCpu()) {
                return 1;
            } else {
                return 0;
            }
        } else {

            if (o1.getFreeMemory() > o2.getFreeMemory()) {
                return 1;
            } else if (o2.getFreeMemory() > o1.getFreeMemory()) {
                return -1;
            } else {
                return 0;
            }
        }

    }

}

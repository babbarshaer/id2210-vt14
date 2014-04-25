/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package resourcemanager.system.peer.rm;

/**
 * Enum representing the status of the request.
 *
 * @author babbarshaer
 */
public enum JobStatusEnum {

    RECEIVED("received"),
    REQUESTED("requested"),
    SUBMITTED("submitted"),
    QUEUED("queued"),
    SCHEDULED("scheduled"),
    PROCESSING("processing"),
    EXECUTION("execution"),
    COMPLETED("completed"),
    ABORTED("aborted");

    private final String status;

    private JobStatusEnum(String status) {
        this.status = status;
    }

    public String getStatus() {
        return this.status;
    }

}

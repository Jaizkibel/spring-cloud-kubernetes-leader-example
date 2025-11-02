package com.nbreval.examples.leaderelection.leadership;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Component used to track leadership status for the application.
 * This component is called by {@link LeaseLeaderManager} when leadership is acquired or lost
 * during the Kubernetes Lease-based leader election process.
 * <p>
 * When leadership is granted, the boolean value is set to true, which activates the log in
 * the scheduled task. When leadership is revoked, the value is set to false and the log message
 * is not shown.
 */
@Component
@ConditionalOnProperty(prefix = "spring.cloud.kubernetes.leader", name = "enabled", havingValue = "true")
public class LeaderEvents {

    /**
     * Variable to mark instance as leader, or not
     */
    private final AtomicBoolean isLeader = new AtomicBoolean(false);

    /**
     * Marks this instance as the leader. Called by {@link LeaseLeaderManager} when leadership is acquired.
     */
    public void grantLeadership() {
        isLeader.set(true);
    }

    /**
     * Marks this instance as not the leader. Called by {@link LeaseLeaderManager} when leadership is lost.
     */
    public void revokeLeadership() {
        isLeader.set(false);
    }

    /**
     * Used by the rest of application to check if current instance is the leader.
     * @return True if current instance is the leader, else false
     */
    public boolean isLeader() {
        return isLeader.get();
    }
}

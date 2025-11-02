package com.nbreval.examples.leaderelection.leadership;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Component that manages leader election using Kubernetes Leases via the Fabric8 client.
 * This replaces the ConfigMap-based leader election from spring-cloud-kubernetes-fabric8-leader.
 * <p>
 * The leader election process runs in a background thread and uses callbacks to notify
 * when leadership is acquired or lost.
 */
@Component
@ConditionalOnProperty(prefix = "spring.cloud.kubernetes.leader", name = "enabled", havingValue = "true")
public class LeaseLeaderManager implements ApplicationListener<ContextClosedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(LeaseLeaderManager.class);

    @Autowired
    private LeaderEvents leaderEvents;

    @Value("${app.leader.lease-name:leader-example}")
    private String leaseName;

    @Value("${app.leader.lease-duration-seconds:30}")
    private int leaseDurationSeconds;

    @Value("${app.leader.renew-deadline-seconds:20}")
    private int renewDeadlineSeconds;

    @Value("${app.leader.retry-period-millis:5000}")
    private long retryPeriodMillis;

    private KubernetesClient kubernetesClient;
    private ExecutorService executorService;
    private volatile boolean running = false;

    /**
     * Initializes and starts the leader election process after bean construction.
     * Creates a Kubernetes client, configures the lease-based leader elector, and
     * starts the election process in a background thread.
     */
    @PostConstruct
    public void startLeaderElection() {
        try {
            // Build Kubernetes client configuration
            Config config = new ConfigBuilder().build();
            kubernetesClient = new KubernetesClientBuilder().withConfig(config).build();

            // Get identity from environment (pod name) or use hostname
            String identity = System.getenv().getOrDefault("HOSTNAME", "unknown-" + System.currentTimeMillis());

            // Get namespace from service account or use default
            String namespace = kubernetesClient.getNamespace();
            if (namespace == null || namespace.isEmpty()) {
                namespace = "default";
            }

            logger.info("Starting Lease-based leader election: leaseName={}, namespace={}, identity={}, " +
                    "leaseDuration={}s, renewDeadline={}s, retryPeriod={}ms",
                    leaseName, namespace, identity, leaseDurationSeconds, renewDeadlineSeconds, retryPeriodMillis);

            // Create lease lock for leader election
            LeaseLock leaseLock = new LeaseLock(namespace, leaseName, identity);

            // Configure leader election with callbacks
            var leaderElectionConfig = new LeaderElectionConfigBuilder()
                    .withName(leaseName)
                    .withLeaseDuration(Duration.ofSeconds(leaseDurationSeconds))
                    .withRenewDeadline(Duration.ofSeconds(renewDeadlineSeconds))
                    .withRetryPeriod(Duration.ofMillis(retryPeriodMillis))
                    .withLeaderCallbacks(new LeaderCallbacks(
                            this::onStartLeading,
                            this::onStopLeading,
                            this::onNewLeader
                    ))
                    .withLock(leaseLock)
                    .build();

            // Start leader election in background thread
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "leader-election");
                thread.setDaemon(true);
                return thread;
            });

            running = true;
            executorService.submit(() -> {
                try {
                    kubernetesClient.leaderElector()
                            .withConfig(leaderElectionConfig)
                            .build()
                            .run();
                } catch (Exception e) {
                    logger.error("Leader election failed", e);
                }
            });

            logger.info("Leader election started successfully");

        } catch (Exception e) {
            logger.error("Failed to start leader election", e);
            throw new RuntimeException("Failed to initialize leader election", e);
        }
    }

    /**
     * Callback invoked when this instance becomes the leader.
     */
    private void onStartLeading() {
        logger.info("Leadership acquired");
        leaderEvents.grantLeadership();
    }

    /**
     * Callback invoked when this instance loses leadership.
     */
    private void onStopLeading() {
        logger.info("Leadership lost");
        leaderEvents.revokeLeadership();
    }

    /**
     * Callback invoked when a new leader is elected (may be this instance or another).
     * @param identity The identity of the new leader
     */
    private void onNewLeader(String identity) {
        logger.debug("New leader elected: {}", identity);
    }

    /**
     * Handles application shutdown by stopping the leader election process
     * and cleaning up resources.
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        shutdown();
    }

    /**
     * Stops the leader election process and releases resources.
     */
    private void shutdown() {
        if (running) {
            logger.info("Shutting down leader election");
            running = false;

            if (executorService != null) {
                executorService.shutdownNow();
            }

            if (kubernetesClient != null) {
                kubernetesClient.close();
            }
        }
    }
}

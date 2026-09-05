package org.eqasim.jakarta.routing;

import java.util.concurrent.atomic.LongAdder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;

/** Thread-safe aggregate diagnostics; emits one summary when the controller stops. */
public final class HomeSideRaptorDiagnostics implements ShutdownListener {
    private static final Logger LOG = LogManager.getLogger(HomeSideRaptorDiagnostics.class);

    private final LongAdder accessMotorcycleCandidates = new LongAdder();
    private final LongAdder accessMotorcycleCandidatesRemoved = new LongAdder();
    private final LongAdder egressMotorcycleCandidates = new LongAdder();
    private final LongAdder egressMotorcycleCandidatesRemoved = new LongAdder();
    private final LongAdder missingEndpointMetadata = new LongAdder();
    private final LongAdder uninspectableFeederCandidates = new LongAdder();

    public void recordMotorcycleCandidate(boolean access) {
        (access ? accessMotorcycleCandidates : egressMotorcycleCandidates).increment();
    }

    public void recordRemovedMotorcycleCandidate(boolean access) {
        (access ? accessMotorcycleCandidatesRemoved : egressMotorcycleCandidatesRemoved).increment();
    }

    public void recordMissingEndpointMetadata() {
        missingEndpointMetadata.increment();
    }

    public void recordUninspectableFeederCandidate() {
        uninspectableFeederCandidates.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                accessMotorcycleCandidates.sum(),
                accessMotorcycleCandidatesRemoved.sum(),
                egressMotorcycleCandidates.sum(),
                egressMotorcycleCandidatesRemoved.sum(),
                missingEndpointMetadata.sum(),
                uninspectableFeederCandidates.sum());
    }

    @Override
    public void notifyShutdown(ShutdownEvent event) {
        Snapshot snapshot = snapshot();
        LOG.info(
                "Jakarta PT home-side routing summary: accessMotorcycleCandidates={} "
                        + "accessMotorcycleCandidatesRemoved={} egressMotorcycleCandidates={} "
                        + "egressMotorcycleCandidatesRemoved={} missingEndpointMetadata={} "
                        + "uninspectableFeederCandidates={}",
                snapshot.accessMotorcycleCandidates(),
                snapshot.accessMotorcycleCandidatesRemoved(),
                snapshot.egressMotorcycleCandidates(),
                snapshot.egressMotorcycleCandidatesRemoved(),
                snapshot.missingEndpointMetadata(),
                snapshot.uninspectableFeederCandidates());
    }

    public record Snapshot(
            long accessMotorcycleCandidates,
            long accessMotorcycleCandidatesRemoved,
            long egressMotorcycleCandidates,
            long egressMotorcycleCandidatesRemoved,
            long missingEndpointMetadata,
            long uninspectableFeederCandidates) {
    }
}

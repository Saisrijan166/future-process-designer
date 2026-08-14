package com.assesswise.processdesigner.service;

import com.assesswise.processdesigner.domain.BusinessProcess;
import com.assesswise.processdesigner.exception.AccessDeniedForResourceException;
import com.assesswise.processdesigner.exception.ResourceNotFoundException;
import com.assesswise.processdesigner.repository.BusinessProcessRepository;
import com.assesswise.processdesigner.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place the ownership rules are decided.
 *
 * <p>Every route that touches one specific process goes through here, so the rules cannot drift
 * apart between endpoints — which is the usual way authorisation bugs happen.
 *
 * <p>The rules:
 * <ul>
 *   <li><b>Read and analyse</b> — your own processes, and the shared samples.
 *   <li><b>Edit and delete</b> — your own processes only. The samples belong to nobody and stay
 *       as they are, so one person cannot remove the demo data for everyone.
 * </ul>
 *
 * <p>Someone else's process returns <b>404, not 403</b>: a 403 would confirm that a process with
 * that id exists, which is information the caller is not entitled to.
 */
@Service
public class ProcessAccessService {

    private final BusinessProcessRepository processRepository;

    public ProcessAccessService(BusinessProcessRepository processRepository) {
        this.processRepository = processRepository;
    }

    /** For reading and for running the analysis: own process or shared sample. */
    @Transactional(readOnly = true)
    public BusinessProcess requireReadable(UUID processId, CurrentUser user) {
        BusinessProcess process = load(processId);
        if (process.isSample() || process.isOwnedBy(user.id())) {
            return process;
        }
        throw hiddenFrom(processId);
    }

    /** For editing and deleting: own process only. */
    @Transactional(readOnly = true)
    public BusinessProcess requireWritable(UUID processId, CurrentUser user) {
        BusinessProcess process = load(processId);
        if (process.isOwnedBy(user.id())) {
            return process;
        }
        if (process.isSample()) {
            throw new AccessDeniedForResourceException(
                    "This is a shared sample process. Everyone can read and analyse it, but it cannot be "
                            + "edited or deleted. Create your own process to change something.");
        }
        throw hiddenFrom(processId);
    }

    private BusinessProcess load(UUID processId) {
        return processRepository.findById(processId)
                .orElseThrow(() -> ResourceNotFoundException.of("Process", processId));
    }

    /**
     * Deliberately indistinguishable from a process that does not exist, so the API cannot be used
     * to probe for other people's ids.
     */
    private ResourceNotFoundException hiddenFrom(UUID processId) {
        return ResourceNotFoundException.of("Process", processId);
    }
}

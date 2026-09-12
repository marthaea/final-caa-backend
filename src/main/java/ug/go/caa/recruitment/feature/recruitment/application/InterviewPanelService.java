package ug.go.caa.recruitment.feature.recruitment.application;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ug.go.caa.recruitment.feature.identity.application.AuthorizationService;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.InterviewPanelRepository;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.InterviewPanelRepository.PanelMember;
import ug.go.caa.recruitment.feature.recruitment.infrastructure.InterviewPanelRepository.StaffContact;
import ug.go.caa.recruitment.feature.support.infrastructure.SupportRepository;
import ug.go.caa.recruitment.shared.persistence.AuditWriter;
import ug.go.caa.recruitment.shared.persistence.OutboxWriter;
import ug.go.caa.recruitment.shared.security.AuthenticatedActor;
import ug.go.caa.recruitment.shared.web.ApiException;

@Service
public class InterviewPanelService {

    private static final String DEFAULT_INVITE_TEMPLATE =
            "Dear {name},\n\nYou have been selected by {invitedBy} to serve on the interview panel for "
                    + "the position of {role} at the Uganda Civil Aviation Authority.\n\nPlease log in to "
                    + "the HR Console for panel scheduling details, or contact Human Resources for more "
                    + "information.\n\nThank you for your service to the selection process.\n\n"
                    + "Yours sincerely,\nHuman Resources Department\nUganda Civil Aviation Authority";

    private final InterviewPanelRepository repository;
    private final AuthorizationService authorization;
    private final AuditWriter audit;
    private final OutboxWriter outbox;
    private final SupportRepository supportRepository;

    public InterviewPanelService(
            InterviewPanelRepository repository,
            AuthorizationService authorization,
            AuditWriter audit,
            OutboxWriter outbox,
            SupportRepository supportRepository
    ) {
        this.repository = repository;
        this.authorization = authorization;
        this.audit = audit;
        this.outbox = outbox;
        this.supportRepository = supportRepository;
    }

    public List<PanelMember> findByJob(long jobId, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canShortlist");
        return repository.findByJob(jobId);
    }

    @Transactional
    public PanelMember add(long jobId, long staffId, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canShortlist");
        String jobTitle = repository.jobTitle(jobId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Job not found"));
        StaffContact staff = repository.staffContact(staffId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Staff member not found"));
        if (repository.isMember(jobId, staffId)) {
            throw new ApiException(HttpStatus.CONFLICT, "This staff member is already on the panel for this job");
        }
        PanelMember saved = repository.add(jobId, staffId, actor.id(), actor.displayName());
        audit.write(actor, "Added interview panelist", staff.firstName() + " " + staff.lastName() + " — " + jobTitle);

        if (staff.email() != null && !staff.email().isBlank()) {
            outbox.write("interview_panel_members", saved.id(), "email.delivery-requested", Map.of(
                    "to", staff.email(),
                    "candidateName", staff.firstName(),
                    "subject", "You've been selected for an interview panel — " + jobTitle,
                    "body", panelInvitationBody(staff.firstName(), jobTitle, actor.displayName()),
                    "trigger", "Interview Panel Invitation",
                    "jobTitle", jobTitle));
        }
        return saved;
    }

    @Transactional
    public void remove(long id, long jobId, AuthenticatedActor actor) {
        authorization.requirePermission(actor, "canShortlist");
        boolean removed = repository.remove(id, jobId);
        if (!removed) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Panel member not found");
        }
        audit.write(actor, "Removed interview panelist", "Panel member #" + id);
    }

    private String panelInvitationBody(String firstName, String jobTitle, String invitedBy) {
        String template = supportRepository.settings()
                .map(SupportRepository.SettingsData::panelInvite)
                .filter(t -> t != null && !t.isBlank())
                .orElse(DEFAULT_INVITE_TEMPLATE);
        return template
                .replace("{name}", firstName)
                .replace("{role}", jobTitle)
                .replace("{invitedBy}", invitedBy);
    }
}

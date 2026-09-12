# Email event types (all handled by `OutboxEmailWorker`)

| Event | Triggered by |
| --- | --- |
| `identity.welcome-requested` | Candidate registration |
| `identity.email-verification-requested` | Register / resend verification |
| `identity.password-reset-requested` | `POST /api/auth/forgot-password` |
| `email.delivery-requested` | Admin Email tab, auto status templates, interview panel, assessments |
| `application.status-notification-requested` | Admin status change with custom notify message |
| `application.intern-acceptance-requested` | Status `Offered` when application has CGPA (intern) |
| `job.submitted-for-review` | Job workflow submit |
| `job.pending-final-approval` | HOD approves department review |
| `job.declined` | Job declined (notifies job creator) |

Requires `SMTP_ENABLED=true` and Brevo on port **2525** on Thewton-Server (587 blocked outbound).

Run automated coverage: `./mvnw test -Dtest=OutboxEmailWorkerIntegrationTest`

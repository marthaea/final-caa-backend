-- Org-wide default CGPA threshold used by the Interns (CGPA) auto-screen when
-- a job has no per-job minCgpa configured in its criteria. Previously this
-- was a hardcoded 3.8 constant in the frontend with no way for HR to change it.
ALTER TABLE settings
    ADD COLUMN default_cgpa_threshold numeric(3,2) NOT NULL DEFAULT 3.8
        CHECK (default_cgpa_threshold >= 0 AND default_cgpa_threshold <= 5);

-- Two more notification triggers (assessment scheduling, interview panel
-- invitations) previously sent fixed Java-string emails with no way for HR
-- to edit them, unlike the 4 templates already editable in Settings.
ALTER TABLE settings ADD COLUMN notif_template_assessment_scheduled text;
ALTER TABLE settings ADD COLUMN notif_template_panel_invite text;

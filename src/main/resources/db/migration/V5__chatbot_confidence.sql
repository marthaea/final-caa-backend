-- Martha's "answered" outcome previously meant nothing about how confident
-- the match actually was — a query that barely cleared the matching engine's
-- keyword-evidence bar looked identical, in HR's review queue, to a solid
-- match. Recording the winning match's keyword score lets HR review queue
-- (Site Analytics -> Martha) surface low-margin "answered" results alongside
-- genuine "needs attention" ones, instead of only ever seeing outcome != answered.
ALTER TABLE chatbot_queries ADD COLUMN confidence integer;

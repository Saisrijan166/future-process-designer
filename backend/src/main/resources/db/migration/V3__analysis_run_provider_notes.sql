-- =====================================================================
-- Records what happened in the provider chain before a run was served.
--
-- The application can now fail over from Gemini to Groq when a free-tier quota
-- runs out. analysis_run.provider and .model already record who answered, but
-- without this column a fallback would be invisible: the row would simply say
-- "groq" with no indication that Gemini was tried first and refused.
--
-- One line per provider that was skipped or that failed, e.g.
--   "gemini failed: Gemini free-tier quota exceeded (429): ..."
-- NULL means the primary provider answered on the first attempt.
-- =====================================================================

ALTER TABLE analysis_run ADD COLUMN provider_notes TEXT;

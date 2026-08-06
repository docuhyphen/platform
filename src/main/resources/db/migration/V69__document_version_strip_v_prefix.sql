-- Version labels are stored as bare ordinals (e.g. "1", "2"); the UI adds the "v"/"Version"
-- prefix at display time. Older rows were stored with a leading "v" (e.g. "v1"), which the
-- UI then rendered as "vv1". Strip a single leading "v" so those rows display correctly.
UPDATE document_version
SET version = substring(version FROM 2)
WHERE version LIKE 'v%';


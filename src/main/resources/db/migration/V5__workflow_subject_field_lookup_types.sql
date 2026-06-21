--
-- V5: add lookupType to UUID subject fields in the workflow trigger event registry.
--
-- APP_USER: searched via the caller's personal contacts (/me/contacts).
-- GROUP:    searched via the caller's personal groups (/me/groups).
-- orgId fields intentionally have no lookupType; org UUID is not user-searchable.
--

UPDATE workflow_trigger_event_registry
SET subject_fields_json = '[{"name":"initiatorId","type":"UUID","description":"App user who created the exchange","lookupType":"APP_USER"},
  {"name":"orgId","type":"UUID","description":"Initiator org id"}]'
WHERE event_name = 'exchange.draft_submitted';

UPDATE workflow_trigger_event_registry
SET subject_fields_json = '[{"name":"recipientId","type":"UUID","description":"Primary recipient user id","lookupType":"APP_USER"},
  {"name":"recipientGroupId","type":"UUID","description":"Recipient group id (GROUP type only)","lookupType":"GROUP"},
  {"name":"initiatorId","type":"UUID","description":"Initiator user id","lookupType":"APP_USER"},
  {"name":"orgId","type":"UUID","description":"Initiator org id"},
  {"name":"recipientType","type":"STRING","description":"EMAIL | APP_USER | GROUP"}]'
WHERE event_name = 'exchange.acceptance_pending';

UPDATE workflow_trigger_event_registry
SET subject_fields_json = '[{"name":"initiatorId","type":"UUID","description":"Initiator user id","lookupType":"APP_USER"},
  {"name":"orgId","type":"UUID","description":"Initiator org id"}]'
WHERE event_name = 'exchange.activated';

UPDATE workflow_trigger_event_registry
SET subject_fields_json = '[{"name":"initiatorId","type":"UUID","description":"Initiator user id","lookupType":"APP_USER"},
  {"name":"orgId","type":"UUID","description":"Initiator org id"}]'
WHERE event_name = 'exchange.ending';

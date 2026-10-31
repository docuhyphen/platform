import {InformationRequestAuthorActionsDto} from "../../../models/models.tsx";

export const noAuthorAction: InformationRequestAuthorActionsDto = {
    issue: false,
    cancel: false,
    supersede: false,
    changeCompletionGate: false,
    manageParties: false,
    previewRecipients: false,
    manageClocks: false,
    scheduleFollowUps: false,
    createFollowUps: false,
    promoteFacts: false,
    revokeFacts: false,
    recordDecisions: false,
    issueAccessLinks: false,
};

export const everyAuthorAction: InformationRequestAuthorActionsDto = {
    issue: true,
    cancel: true,
    supersede: true,
    changeCompletionGate: true,
    manageParties: true,
    previewRecipients: true,
    manageClocks: true,
    scheduleFollowUps: true,
    createFollowUps: true,
    promoteFacts: true,
    revokeFacts: true,
    recordDecisions: true,
    issueAccessLinks: true,
};

export const retainedRecordAuthorActions: InformationRequestAuthorActionsDto = {...noAuthorAction, revokeFacts: true};

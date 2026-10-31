import {InformationRequestResponseWorkspaceDto, InformationRequestState} from "../../../models/models.tsx";
import {mayChangeUnderStanding} from "../../shared/executionStandingText.ts";

type RecoveryWorkspace = Pick<InformationRequestResponseWorkspaceDto, "request" | "executionStanding" | "authorActions">;

const SUBMITTING_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

export const maySubmissionsBeRecovered = (workspace: RecoveryWorkspace): boolean =>
    workspace.authorActions?.manageParties === true &&
    SUBMITTING_STATES.has(workspace.request.state) &&
    mayChangeUnderStanding(workspace.executionStanding);

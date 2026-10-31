import {useEffect, useMemo, useRef} from "react";
import {
    InformationRequestResponseWorkspaceDto,
    InformationRequestTemplateRequirementDto
} from "../../../models/models.tsx";
import {KeptEvidenceUploads} from "../../requirement-evidence/keptEvidenceUploads.ts";
import InformationRequestStructuredResponsePanel
    from "../../structured-response-workspace/InformationRequestStructuredResponsePanel.tsx";
import InformationRequestSubmissionSection
    from "../../submission/follow-up-section/InformationRequestSubmissionSection.tsx";
import ExecutionStandingNotice from "../../shared/execution-standing-notice/ExecutionStandingNotice.tsx";
import ExchangeAcceptanceNotice from "../exchange-acceptance-notice/ExchangeAcceptanceNotice.tsx";
import {PendingWorkContext} from "../../shared/respondentPendingWork.ts";
import {CommandKeysContext} from "../../shared/useStableIdempotencyKey.ts";
import ResponseSectionNavigation from "../response-section-navigation/ResponseSectionNavigation.tsx";
import StepUpPanel from "../step-up-panel/StepUpPanel.tsx";
import {sectionSummaries} from "../sectionSummaries.ts";
import {useRespondentPendingWork} from "../useRespondentPendingWork.ts";
import {useRespondentWorkspaceBodyStyles} from "./RespondentWorkspaceBodyStyles.tsx";

interface Props
{
    workspace: InformationRequestResponseWorkspaceDto;
    requirements: InformationRequestTemplateRequirementDto[];
    accessLinkToken?: string;
    keptUploads: KeptEvidenceUploads;
    commandKeys: Map<string, string>;
    onRefresh: () => Promise<void>;
    onAccessSessionEnded: (refusal: unknown) => void;
}

const RespondentWorkspaceBody = ({
                                     workspace,
                                     requirements,
                                     accessLinkToken,
                                     keptUploads,
                                     commandKeys,
                                     onRefresh,
                                     onAccessSessionEnded,
                                 }: Props) =>
{
    const styles = useRespondentWorkspaceBodyStyles();
    const {reporter, pending} = useRespondentPendingWork();
    const seenEvidenceRevision = useRef(pending.evidenceRevision);
    useEffect(() => {
        if (pending.evidenceRevision === seenEvidenceRevision.current) return;
        seenEvidenceRevision.current = pending.evidenceRevision;
        void onRefresh();
    }, [onRefresh, pending.evidenceRevision]);
    const summaries = useMemo(
        () => sectionSummaries(workspace.templateVersion.sections, workspace.responses),
        [workspace.responses, workspace.templateVersion.sections],
    );

    return (
        <CommandKeysContext.Provider value={commandKeys}>
            <div id={"information-request-respondent-workspace-shell"}
                 className={styles.body}>
                <ResponseSectionNavigation summaries={summaries}/>
                <div id={"information-request-respondent-main"}
                     className={styles.main}>
                    <ExchangeAcceptanceNotice idPrefix={"information-request-respondent"}
                                              accepted={workspace.exchangeAccepted}/>
                    <ExecutionStandingNotice idPrefix={"information-request-respondent"}
                                             standing={workspace.executionStanding}/>
                    <StepUpPanel requestId={workspace.request.id}
                                 awaiting={workspace.requirementsAwaitingStepUp}
                                 accessLinkToken={accessLinkToken}
                                 onVerified={onRefresh}
                                 onAccessSessionEnded={onAccessSessionEnded}/>
                    <PendingWorkContext.Provider value={reporter}>
                        <InformationRequestStructuredResponsePanel request={workspace.request}
                                                                   respondentKey={workspace.respondentKey}
                                                                   responseETag={workspace.responseETag}
                                                                   groups={workspace.templateVersion.groups}
                                                                   creatableGroupKeys={workspace.creatableGroupKeys ?? []}
                                                                   occurrences={workspace.occurrences}
                                                                   requirements={requirements}
                                                                   bindings={workspace.schemaAssignment?.bindings ?? []}
                                                                   responses={workspace.responses}
                                                                   supportingEvidenceLinks={workspace.supportingEvidenceLinks}
                                                                   evidenceUploadAvailable={workspace.evidenceUploadAvailable}
                                                                   evidenceMalwareScanning={workspace.evidenceMalwareScanning}
                                                                   readOnly={workspace.exchangeAccepted === false}
                                                                   accessLinkToken={accessLinkToken}
                                                                   keptUploads={keptUploads}
                                                                   onRefresh={onRefresh}
                                                                   onAccessSessionEnded={onAccessSessionEnded}/>
                    </PendingWorkContext.Provider>
                    <InformationRequestSubmissionSection workspace={workspace}
                                                         requirements={requirements}
                                                         accessLinkToken={accessLinkToken}
                                                         pendingWork={pending}
                                                         onChanged={onRefresh}/>
                </div>
            </div>
        </CommandKeysContext.Provider>
    );
};

export default RespondentWorkspaceBody;

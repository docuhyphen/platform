import {useMemo} from "react";
import {InformationRequestResponseWorkspaceDto, InformationRequestTemplateRequirementDto} from "../../../models/models.tsx";
import InformationRequestStructuredResponsePanel from "../../structured-response-workspace/InformationRequestStructuredResponsePanel.tsx";
import InformationRequestSubmissionSection from "../../submission/follow-up-section/InformationRequestSubmissionSection.tsx";
import ExecutionStandingNotice from "../../shared/execution-standing-notice/ExecutionStandingNotice.tsx";
import ResponseSectionNavigation from "../response-section-navigation/ResponseSectionNavigation.tsx";
import {sectionSummaries} from "../sectionSummaries.ts";
import {useRespondentWorkspaceBodyStyles} from "./RespondentWorkspaceBodyStyles.tsx";

interface Props
{
    workspace: InformationRequestResponseWorkspaceDto;
    requirements: InformationRequestTemplateRequirementDto[];
    accessLinkToken?: string;
    onRefresh: () => void;
}

const RespondentWorkspaceBody = ({workspace, requirements, accessLinkToken, onRefresh}: Props) =>
{
    const styles = useRespondentWorkspaceBodyStyles();
    const summaries = useMemo(
        () => sectionSummaries(workspace.templateVersion.sections, workspace.responses),
        [workspace.responses, workspace.templateVersion.sections],
    );

    return (
        <div id={"information-request-respondent-workspace-shell"}
             className={styles.body}>
            <ResponseSectionNavigation summaries={summaries}/>
            <div id={"information-request-respondent-main"}
                 className={styles.main}>
                <ExecutionStandingNotice idPrefix={"information-request-respondent"}
                                         standing={workspace.executionStanding}/>
                <InformationRequestStructuredResponsePanel request={workspace.request}
                                                           responseETag={workspace.responseETag}
                                                           groups={workspace.templateVersion.groups}
                                                           conditionRules={workspace.templateVersion.conditionRules}
                                                           occurrences={workspace.occurrences}
                                                           requirements={requirements}
                                                           bindings={workspace.schemaAssignment?.bindings ?? []}
                                                           responses={workspace.responses}
                                                           evidenceUploadAvailable={workspace.evidenceUploadAvailable}
                                                           evidenceMalwareScanning={workspace.evidenceMalwareScanning}
                                                           accessLinkToken={accessLinkToken}
                                                           onRefresh={onRefresh}/>
                <InformationRequestSubmissionSection workspace={workspace}
                                                     requirements={requirements}
                                                     accessLinkToken={accessLinkToken}
                                                     onChanged={onRefresh}/>
            </div>
        </div>
    );
};

export default RespondentWorkspaceBody;

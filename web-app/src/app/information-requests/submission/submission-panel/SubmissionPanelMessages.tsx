import {MessageBar, MessageBarBody} from "@fluentui/react-components";
import {InformationRequestState, InformationRequestSubmissionResultDto} from "../../../models/models.tsx";

interface Props
{
    correctionOpen: boolean;
    error: string | null;
    result: InformationRequestSubmissionResultDto | null;
}

const SubmissionPanelMessages = ({correctionOpen, error, result}: Props) => (
    <>
        {correctionOpen && (
            <MessageBar id={"information-request-submission-correction"}
                        intent={"info"}>
                <MessageBarBody id={"information-request-submission-correction-body"}>
                    This part was returned for correction. Correct the items listed under Review results, then
                    submit it again.
                </MessageBarBody>
            </MessageBar>
        )}
        {error && (
            <MessageBar id={"information-request-submission-error"}
                        intent={"error"}>
                <MessageBarBody id={"information-request-submission-error-body"}>{error}</MessageBarBody>
            </MessageBar>
        )}
        {result && (
            <MessageBar id={"information-request-submission-result"}
                        intent={"success"}>
                <MessageBarBody id={"information-request-submission-result-body"}>
                    {`Submission ${result.submission.packageNumber} was recorded.`}
                    {result.requestState === InformationRequestState.CLOSED && " This request is now complete."}
                </MessageBarBody>
            </MessageBar>
        )}
    </>
);

export default SubmissionPanelMessages;

import {Button, Dropdown, Field, MessageBar, MessageBarBody, Option, Text} from "@fluentui/react-components";
import {InformationRequestState, InformationRequestSubmissionMode} from "../../../models/models.tsx";
import SubmissionAttestationCard from "../submission-attestation-card/SubmissionAttestationCard.tsx";
import SubmissionPackageList from "../submission-package-list/SubmissionPackageList.tsx";
import SubmissionReadinessList from "../submission-readiness-list/SubmissionReadinessList.tsx";
import {humanizedKey} from "../submissionLabels.ts";
import {useInformationRequestSubmission} from "../useInformationRequestSubmission.ts";
import {useInformationRequestSubmissionPanelStyles} from "./InformationRequestSubmissionPanelStyles.tsx";

interface Props
{
    requestId: string;
    requestState: InformationRequestState;
    responseETag: string;
    accessLinkToken?: string;
    onChanged: () => void;
}

const InformationRequestSubmissionPanel = ({requestId, requestState, responseETag, accessLinkToken, onChanged}: Props) =>
{
    const styles = useInformationRequestSubmissionPanelStyles();
    const {preview, result, busy, error, selectStage, submit, attest, withdraw} =
        useInformationRequestSubmission(requestId, responseETag, accessLinkToken, onChanged);
    const closed = requestState === InformationRequestState.CLOSED;

    if (!preview) return null;

    return (
        <section id={"information-request-submission-panel"}
                 className={styles.panel}>
            <div id={"information-request-submission-header"}
                 className={styles.header}>
                <Text id={"information-request-submission-title"}
                      size={500}
                      weight={"semibold"}>
                    Review and submit
                </Text>
                {preview.submissionMode === InformationRequestSubmissionMode.STAGED && preview.stageKey && (
                    <Field id={"information-request-submission-stage-field"}
                           label={"Part to submit"}
                           className={styles.stageField}>
                        <Dropdown id={"information-request-submission-stage"}
                                  value={humanizedKey(preview.stageKey)}
                                  selectedOptions={[preview.stageKey]}
                                  disabled={busy}
                                  onOptionSelect={(_, data) => data.optionValue && void selectStage(data.optionValue)}>
                            {preview.stages.map(stage => (
                                <Option id={`information-request-submission-stage-${stage.stageKey}`}
                                        key={stage.stageKey}
                                        value={stage.stageKey}
                                        text={humanizedKey(stage.stageKey)}>
                                    {`${humanizedKey(stage.stageKey)}${stage.submitted ? " (submitted)" : ""}`}
                                </Option>
                            ))}
                        </Dropdown>
                    </Field>
                )}
            </div>
            {error && (
                <MessageBar id={"information-request-submission-error"}
                            intent={"error"}>
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}
            {result && (
                <MessageBar id={"information-request-submission-result"}
                            intent={"success"}>
                    <MessageBarBody>
                        {`Submission ${result.submission.packageNumber} was recorded.`}
                        {result.requestState === InformationRequestState.CLOSED && " This request is now complete."}
                    </MessageBarBody>
                </MessageBar>
            )}
            {!closed && (
                <SubmissionReadinessList ready={preview.ready}
                                         problems={preview.problems}
                                         undisclosedProblemCount={preview.undisclosedProblemCount}/>
            )}
            {!closed && preview.attestations.map(status => (
                <SubmissionAttestationCard key={status.requirementId}
                                           status={status}
                                           busy={busy}
                                           onAttest={input => void attest(input)}/>
            ))}
            {!closed && (
                <div id={"information-request-submission-actions"}
                     className={styles.actions}>
                    <Button id={"information-request-submission-submit"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={busy || !preview.canSubmit}
                            onClick={() => void submit()}>
                        Submit
                    </Button>
                    {!preview.canSubmit && preview.ready && (
                        <Text id={"information-request-submission-unavailable"}
                              className={styles.detail}>
                            This part cannot be submitted now.
                        </Text>
                    )}
                </div>
            )}
            <SubmissionPackageList packages={preview.packages}
                                   busy={busy}
                                   closed={closed}
                                   onWithdraw={packageId => void withdraw(packageId)}/>
        </section>
    );
};

export default InformationRequestSubmissionPanel;

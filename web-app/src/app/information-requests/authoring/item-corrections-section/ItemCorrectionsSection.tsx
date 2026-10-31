import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestPartyDto} from "../../../models/models.tsx";
import {reviewValueText} from "../../review/reviewLabels.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import CorrectItemDialog from "../correct-item-dialog/CorrectItemDialog.tsx";
import {partyLabel} from "../author-workspace/useAuthorWorkspace.ts";
import {correctableAnswers, PromotableAnswer} from "../request-outcomes-panel/outcomeLabels.ts";
import {RequestOutcomes} from "../request-outcomes-panel/useRequestOutcomes.ts";
import {useItemCorrectionsSectionStyles} from "./ItemCorrectionsSectionStyles.tsx";
import {RecordedCorrections} from "./useRecordedCorrections.ts";
import {useDialogCommand} from "../../shared/useDialogCommand.ts";

interface Props
{
    outcomes: RequestOutcomes;
    subjects: InformationRequestPartyDto[];
    recorded: RecordedCorrections;
    labelOf: (requirementId: string, requirementKey?: string) => string;
}

const ItemCorrectionsSection = ({outcomes, subjects, recorded, labelOf}: Props) =>
{
    const styles = useItemCorrectionsSectionStyles();
    const [correcting, setCorrecting] = useState<PromotableAnswer | null>(null);
    const command = useDialogCommand(outcomes.error, outcomes.notice);
    const answers = correctableAnswers(outcomes.packages);
    const subjectChoices = subjects.map(subject => ({
        value: subject.subjectIdentityRefId ?? "",
        label: partyLabel(subject)
    }));

    return (
        <section id={"information-request-item-corrections"}
                 aria-labelledby={"information-request-item-corrections-title"}
                 className={styles.section}>
            <Text id={"information-request-item-corrections-title"}
                  as={"h3"}
                  size={400}
                  weight={"semibold"}
                  className={styles.heading}>
                Corrections
            </Text>
            <Text id={"information-request-item-corrections-explanation"}
                  className={styles.muted}>
                When the subject asks for a submitted answer to be corrected, record the corrected content here.
            </Text>
            {recorded.error && (
                <InformationRequestLoadError id={"information-request-item-corrections-load-error"}
                                             message={recorded.error}
                                             onRetry={() => void recorded.reload()}/>
            )}
            {answers.length === 0 && (
                <Text id={"information-request-item-corrections-empty"}>No submitted answer holds content to correct.</Text>
            )}
            {answers.length > 0 && (
                <ul id={"information-request-item-corrections-list"}
                    aria-labelledby={"information-request-item-corrections-title"}
                    className={styles.list}>
                    {answers.map(answer =>
                    {
                        const id = `information-request-item-correction-${answer.item.id}`;
                        const label = labelOf(answer.item.requirementId, answer.item.requirementKey);
                        return (
                            <li id={id}
                                key={`${answer.packageId}-${answer.item.id}`}
                                className={styles.row}>
                                <div id={`${id}-text`}
                                     className={styles.text}>
                                    <Text id={`${id}-label`}
                                          weight={"semibold"}>
                                        {label}
                                    </Text>
                                    <Text id={`${id}-content`}>
                                        {answer.item.fieldValue !== undefined && answer.item.fieldValue !== null
                                            ? reviewValueText(answer.item.fieldValue)
                                            : answer.item.narrative}
                                    </Text>
                                    {recorded.corrections.filter(correction => correction.submissionItemId === answer.item.id).map(correction => (
                                        <Text id={`${id}-recorded-${correction.id}`}
                                              key={correction.id}
                                              size={200}
                                              className={styles.muted}>
                                            {`Corrected for ${correction.subjectLabel} on ${formatInformationRequestTime(correction.recordedAt)}. `
                                                + `Reason: ${correction.reasonCode}`}
                                        </Text>
                                    ))}
                                </div>
                                <Button id={`${id}-correct`}
                                        appearance={"subtle"}
                                        shape={"circular"}
                                        disabled={outcomes.busy}
                                        aria-label={`Correct ${label}`}
                                        onClick={() => {
                                            command.reset();
                                            setCorrecting(answer);
                                        }}>
                                    Correct
                                </Button>
                            </li>
                        );
                    })}
                </ul>
            )}
            {correcting && (
                <CorrectItemDialog item={correcting.item}
                                   label={labelOf(correcting.item.requirementId, correcting.item.requirementKey)}
                                   subjects={subjectChoices}
                                   busy={outcomes.busy || command.busy}
                                   error={command.error}
                                   onConfirm={(correction, basis, subjectId) => void command.run(
                                       () => outcomes.correct(subjectId, correction, basis), () => {
                                           setCorrecting(null);
                                           void recorded.reload();
                                       })}
                                   onDismiss={() => setCorrecting(null)}/>
            )}
        </section>
    );
};

export default ItemCorrectionsSection;

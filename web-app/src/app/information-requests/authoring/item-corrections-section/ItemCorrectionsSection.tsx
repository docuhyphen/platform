import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {reviewValueText} from "../../review/reviewLabels.ts";
import CorrectItemDialog from "../correct-item-dialog/CorrectItemDialog.tsx";
import {correctableAnswers, PromotableAnswer} from "../request-outcomes-panel/outcomeLabels.ts";
import {RequestOutcomes} from "../request-outcomes-panel/useRequestOutcomes.ts";
import {useItemCorrectionsSectionStyles} from "./ItemCorrectionsSectionStyles.tsx";

interface Props
{
    outcomes: RequestOutcomes;
    subjectId: string;
    labelOf: (requirementId: string, requirementKey?: string) => string;
}

const ItemCorrectionsSection = ({outcomes, subjectId, labelOf}: Props) =>
{
    const styles = useItemCorrectionsSectionStyles();
    const [correcting, setCorrecting] = useState<PromotableAnswer | null>(null);
    const answers = correctableAnswers(outcomes.packages);

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
                                        {answer.item.fieldValue !== undefined ? reviewValueText(answer.item.fieldValue) : answer.item.narrative}
                                    </Text>
                                </div>
                                <Button id={`${id}-correct`}
                                        appearance={"subtle"}
                                        shape={"circular"}
                                        disabled={outcomes.busy}
                                        aria-label={`Correct ${label}`}
                                        onClick={() => setCorrecting(answer)}>
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
                                   busy={outcomes.busy}
                                   onConfirm={(correction, basis) =>
                                   {
                                       setCorrecting(null);
                                       void outcomes.correct(subjectId, correction, basis);
                                   }}
                                   onDismiss={() => setCorrecting(null)}/>
            )}
        </section>
    );
};

export default ItemCorrectionsSection;

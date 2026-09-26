import {Button, Field, Radio, RadioGroup, Text, Textarea} from "@fluentui/react-components";
import {InformationRequestReviewOutcome} from "../../../models/models.tsx";
import {outcomeNeedsFinding, outcomeNeedsNarrative, reviewOutcomeLabels} from "../reviewLabels.ts";
import {ReviewWorksheetDraft} from "../useInformationRequestReview.ts";
import {useReviewItemDecisionControlsStyles} from "./ReviewItemDecisionControlsStyles.tsx";

interface Props
{
    itemId: string;
    draft?: ReviewWorksheetDraft;
    busy: boolean;
    hasFinding: boolean;
    onChange: (change: Partial<ReviewWorksheetDraft>) => void;
    onAddFinding: () => void;
}

const NARRATIVE_LIMIT = 4000;
const OUTCOMES = Object.values(InformationRequestReviewOutcome);

const ReviewItemDecisionControls = ({itemId, draft, busy, hasFinding, onChange, onAddFinding}: Props) =>
{
    const styles = useReviewItemDecisionControlsStyles();
    const id = `information-request-review-item-${itemId}-decision`;
    const outcome = draft?.outcome;

    return (
        <div id={id}
             className={styles.controls}>
            <Field id={`${id}-outcome-field`}
                   label={"Your decision"}>
                <RadioGroup id={`${id}-outcome`}
                            layout={"horizontal"}
                            className={styles.outcomes}
                            value={outcome ?? ""}
                            disabled={busy}
                            onChange={(_, data) => onChange({outcome: OUTCOMES.find(value => value === data.value)})}>
                    {OUTCOMES.map(value => (
                        <Radio id={`${id}-outcome-${value}`}
                               key={value}
                               value={value}
                               label={reviewOutcomeLabels[value]}/>
                    ))}
                </RadioGroup>
            </Field>
            <Field id={`${id}-narrative-field`}
                   label={"Reason for your decision"}
                   hint={outcomeNeedsNarrative(outcome) ? "Required for this decision." : undefined}>
                <Textarea id={`${id}-narrative`}
                          value={draft?.narrative ?? ""}
                          maxLength={NARRATIVE_LIMIT}
                          disabled={busy}
                          onChange={(_, data) => onChange({narrative: data.value})}/>
            </Field>
            <div id={`${id}-actions`}
                 className={styles.actions}>
                {outcomeNeedsFinding(outcome) && !hasFinding && (
                    <Text id={`${id}-finding-hint`}
                          className={styles.hint}>
                        Record a finding that says what must change before recording this decision.
                    </Text>
                )}
                <Button id={`${id}-add-finding`}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={onAddFinding}>
                    Add finding
                </Button>
            </div>
        </div>
    );
};

export default ReviewItemDecisionControls;

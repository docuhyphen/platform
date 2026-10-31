import {Button, Text} from "@fluentui/react-components";
import {reviewValueText} from "../../review/reviewLabels.ts";
import {PromotionCandidate} from "../request-outcomes-panel/promotionEligibility.ts";
import {usePromotableAnswerRowStyles} from "./PromotableAnswerRowStyles.tsx";

interface Props
{
    candidate: PromotionCandidate;
    label: string;
    busy: boolean;
    onPromote: () => void;
}

const PromotableAnswerRow = ({candidate, label, busy, onPromote}: Props) =>
{
    const styles = usePromotableAnswerRowStyles();
    const {answer, blockedReason, promotedPurposes} = candidate;
    const id = `information-request-promotable-answer-${answer.item.id}`;

    return (
        <li id={id}
            className={styles.row}>
            <div id={`${id}-text`}
                 className={styles.text}>
                <Text id={`${id}-label`}
                      weight={"semibold"}>
                    {label}
                </Text>
                <Text id={`${id}-value`}>{reviewValueText(answer.item.fieldValue)}</Text>
                <Text id={`${id}-package`}
                      size={200}
                      className={styles.muted}>
                    {`Submission ${answer.packageNumber}`}
                </Text>
                {promotedPurposes.length > 0 && (
                    <Text id={`${id}-promoted`}
                          size={200}
                          className={styles.muted}>
                        {`Promoted for ${promotedPurposes.join(", ")}`}
                    </Text>
                )}
            </div>
            {blockedReason
                ? (
                    <Text id={`${id}-blocked`}
                          size={200}
                          className={styles.muted}>
                        {blockedReason}
                    </Text>
                )
                : (
                    <Button id={`${id}-promote`}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={busy}
                            aria-label={`Promote ${label}`}
                            onClick={onPromote}>
                        Promote
                    </Button>
                )}
        </li>
    );
};

export default PromotableAnswerRow;

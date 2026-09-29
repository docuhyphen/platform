import {Button, Text} from "@fluentui/react-components";
import {reviewValueText} from "../../review/reviewLabels.ts";
import {useAcceptedFactsSectionStyles} from "../accepted-facts-section/AcceptedFactsSectionStyles.tsx";
import {PromotableAnswer} from "../request-outcomes-panel/outcomeLabels.ts";

interface Props
{
    answer: PromotableAnswer;
    label: string;
    busy: boolean;
    onPromote: () => void;
}

const PromotableAnswerRow = ({answer, label, busy, onPromote}: Props) =>
{
    const styles = useAcceptedFactsSectionStyles();
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
            </div>
            <Button id={`${id}-promote`}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={busy}
                    aria-label={`Promote ${label}`}
                    onClick={onPromote}>
                Promote
            </Button>
        </li>
    );
};

export default PromotableAnswerRow;

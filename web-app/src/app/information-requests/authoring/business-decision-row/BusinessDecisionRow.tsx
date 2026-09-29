import {Button, Text} from "@fluentui/react-components";
import {InformationRequestBusinessDecisionDto, InformationRequestBusinessDecisionKind} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {useBusinessDecisionsSectionStyles} from "../business-decisions-section/BusinessDecisionsSectionStyles.tsx";
import {decisionKindLabels} from "../request-outcomes-panel/outcomeLabels.ts";

interface Props
{
    decision: InformationRequestBusinessDecisionDto;
    canFollowUp: boolean;
    busy: boolean;
    onFollowUp: (kind: InformationRequestBusinessDecisionKind) => void;
}

const references = (decision: InformationRequestBusinessDecisionDto): string =>
    [
        decision.reasonReference ? `Reason reference: ${decision.reasonReference}.` : "",
        decision.externalReference ? `External reference: ${decision.externalReference}.` : "",
    ].filter(Boolean).join(" ");

const BusinessDecisionRow = ({decision, canFollowUp, busy, onFollowUp}: Props) =>
{
    const styles = useBusinessDecisionsSectionStyles();
    const id = `information-request-business-decision-${decision.id}`;
    const process = humanizedKey(decision.owningProcessKey);

    return (
        <li id={id}
            className={styles.row}>
            <div id={`${id}-text`}
                 className={styles.text}>
                <Text id={`${id}-outcome`}
                      weight={"semibold"}>
                    {`${process}: ${humanizedKey(decision.outcomeCode)}`}
                </Text>
                <Text id={`${id}-kind`}
                      size={200}
                      className={styles.muted}>
                    {`${decisionKindLabels[decision.kind]}, revision ${decision.decisionRevision}. Decided ${formatInformationRequestTime(decision.decidedAt)}${decision.recordedByCaller ? ", recorded by you" : ""}.`}
                </Text>
                {references(decision) && (
                    <Text id={`${id}-references`}
                          size={200}
                          className={styles.muted}>
                        {references(decision)}
                    </Text>
                )}
            </div>
            {canFollowUp && (
                <div id={`${id}-actions`}
                     className={styles.actions}>
                    <Button id={`${id}-reconsider`}
                            size={"small"}
                            shape={"circular"}
                            disabled={busy}
                            aria-label={`Reconsider ${process}`}
                            onClick={() => onFollowUp(InformationRequestBusinessDecisionKind.RECONSIDERATION)}>
                        Reconsider
                    </Button>
                    <Button id={`${id}-appeal`}
                            size={"small"}
                            shape={"circular"}
                            disabled={busy}
                            aria-label={`Appeal ${process}`}
                            onClick={() => onFollowUp(InformationRequestBusinessDecisionKind.APPEAL)}>
                        Appeal
                    </Button>
                </div>
            )}
        </li>
    );
};

export default BusinessDecisionRow;

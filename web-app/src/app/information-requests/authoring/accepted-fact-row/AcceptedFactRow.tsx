import {Button, Text} from "@fluentui/react-components";
import {
    InformationRequestAcceptedFactConflictState,
    InformationRequestAcceptedFactDto,
} from "../../../models/models.tsx";
import {reviewValueText} from "../../review/reviewLabels.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {humanizedKey} from "../../submission/submissionLabels.ts";
import {useAcceptedFactsSectionStyles} from "../accepted-facts-section/AcceptedFactsSectionStyles.tsx";
import {confidenceLabels, factVisibilityLabels, freshnessLabels} from "../request-outcomes-panel/outcomeLabels.ts";

interface Props
{
    fact: InformationRequestAcceptedFactDto;
    label: string;
    editable: boolean;
    busy: boolean;
    onRevoke: () => void;
}

const validitySentence = (fact: InformationRequestAcceptedFactDto): string =>
    [
        `Valid from ${formatInformationRequestTime(fact.validFrom)}`,
        fact.validTo ? ` until ${formatInformationRequestTime(fact.validTo)}` : "",
        fact.expiresAt ? `. Stops being offered ${formatInformationRequestTime(fact.expiresAt)}` : "",
        ".",
    ].join("");

const AcceptedFactRow = ({fact, label, editable, busy, onRevoke}: Props) =>
{
    const styles = useAcceptedFactsSectionStyles();
    const id = `information-request-accepted-fact-${fact.id}`;
    const purpose = humanizedKey(fact.purposeKey);

    return (
        <li id={id}
            className={styles.row}>
            <div id={`${id}-text`}
                 className={styles.text}>
                <Text id={`${id}-label`}
                      weight={"semibold"}>
                    {`${label} for ${purpose}`}
                </Text>
                <Text id={`${id}-value`}>{reviewValueText(fact.value)}</Text>
                <Text id={`${id}-policy-basis`}
                      size={200}
                      className={styles.muted}>
                    {`Reuse policy basis: ${fact.policyBasisKey}.`}
                </Text>
                <Text id={`${id}-standing`}
                      size={200}
                      className={styles.muted}>
                    {`${confidenceLabels[fact.confidence]}. ${freshnessLabels[fact.freshness]}. ${factVisibilityLabels[fact.visibility]}.`}
                </Text>
                <Text id={`${id}-validity`}
                      size={200}
                      className={styles.muted}>
                    {validitySentence(fact)}
                </Text>
                {fact.conflictState === InformationRequestAcceptedFactConflictState.CONFLICTING && (
                    <Text id={`${id}-conflict`}
                          size={200}>
                        Differs from another current fact about the same subject.
                    </Text>
                )}
                {fact.supersededByFactId && (
                    <Text id={`${id}-superseded`}
                          size={200}>
                        Replaced by a later fact.
                    </Text>
                )}
                {fact.revoked && (
                    <Text id={`${id}-revoked`}
                          size={200}>
                        {`Revoked ${formatInformationRequestTime(fact.revokedAt)}${fact.revocationReasonCode ? `: ${fact.revocationReasonCode}` : ""}.`}
                    </Text>
                )}
            </div>
            {editable && !fact.revoked && (
                <Button id={`${id}-revoke`}
                        appearance={"subtle"}
                        shape={"circular"}
                        disabled={busy}
                        aria-label={`Revoke ${label} for ${purpose}`}
                        onClick={onRevoke}>
                    Revoke
                </Button>
            )}
        </li>
    );
};

export default AcceptedFactRow;

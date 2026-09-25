import {useEffect, useState} from "react";
import {Badge, Text} from "@fluentui/react-components";
import {getInformationRequestCarryForwards} from "../../../../services/informationRequestSubmissionService.ts";
import {InformationRequestCarryForwardDecision, InformationRequestCarryForwardDto} from "../../../models/models.tsx";
import {carryForwardLabels, invalidationReasonLabels} from "../submissionLabels.ts";
import {useInformationRequestCarryForwardListStyles} from "./InformationRequestCarryForwardListStyles.tsx";

interface Props
{
    requestId: string;
    accessLinkToken?: string;
    requirementLabels: Record<string, string>;
}

const priorAnswer = (carryForward: InformationRequestCarryForwardDto): string =>
{
    if (carryForward.priorFieldValue !== undefined && carryForward.priorFieldValue !== null)
        return typeof carryForward.priorFieldValue === "string"
            ? carryForward.priorFieldValue
            : JSON.stringify(carryForward.priorFieldValue);
    return carryForward.priorNarrative ?? (carryForward.priorDisposition ?? "").toLowerCase().replace(/_/g, " ");
};

const InformationRequestCarryForwardList = ({requestId, accessLinkToken, requirementLabels}: Props) =>
{
    const styles = useInformationRequestCarryForwardListStyles();
    const [carryForwards, setCarryForwards] = useState<InformationRequestCarryForwardDto[]>([]);

    useEffect(() =>
    {
        getInformationRequestCarryForwards(requestId, accessLinkToken)
            .then(setCarryForwards)
            .catch(() => setCarryForwards([]));
    }, [accessLinkToken, requestId]);

    if (carryForwards.length === 0) return null;

    return (
        <section id={"information-request-carry-forwards"}
                 className={styles.section}>
            <Text id={"information-request-carry-forwards-title"}
                  weight={"semibold"}>
                From your previous submission
            </Text>
            <Text id={"information-request-carry-forwards-note"}
                  className={styles.detail}>
                Previous answers are shown for reference only. Save an answer to use it again.
            </Text>
            {carryForwards.map(carryForward => (
                <div id={`information-request-carry-forward-${carryForward.requirementId}`}
                     key={carryForward.requirementId}
                     className={styles.row}>
                    <Text weight={"semibold"}>{requirementLabels[carryForward.requirementId] ?? "Requested item"}</Text>
                    <Badge id={`information-request-carry-forward-${carryForward.requirementId}-decision`}
                           appearance={"tint"}
                           color={carryForward.decision === InformationRequestCarryForwardDecision.OFFERED ? "brand" : "subtle"}>
                        {carryForwardLabels[carryForward.decision]}
                    </Badge>
                    <Text className={styles.detail}>
                        {carryForward.decision === InformationRequestCarryForwardDecision.OFFERED
                            ? priorAnswer(carryForward)
                            : invalidationReasonLabels[carryForward.reasonCode ?? ""] ?? "This needs a new answer."}
                    </Text>
                </div>
            ))}
        </section>
    );
};

export default InformationRequestCarryForwardList;

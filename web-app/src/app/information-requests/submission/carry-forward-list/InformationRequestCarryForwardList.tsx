import {Badge, Text} from "@fluentui/react-components";
import {getInformationRequestCarryForwards} from "../../../../services/informationRequestSubmissionService.ts";
import {InformationRequestCarryForwardDecision} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../shared/informationRequestRefusal.ts";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import {useServerRead} from "../../shared/useServerRead.ts";
import {carryForwardLabels, invalidationReasonLabels} from "../submissionLabels.ts";
import {priorAnswerText, RequirementValueFormat} from "./carryForwardFormatting.ts";
import {useInformationRequestCarryForwardListStyles} from "./InformationRequestCarryForwardListStyles.tsx";

interface Props
{
    requestId: string;
    accessLinkToken?: string;
    requirementLabels: Record<string, string>;
    valueFormats?: Record<string, RequirementValueFormat>;
}

const InformationRequestCarryForwardList = ({
                                                requestId,
                                                accessLinkToken,
                                                requirementLabels,
                                                valueFormats = {}
                                            }: Props) =>
{
    const styles = useInformationRequestCarryForwardListStyles();
    const read = useServerRead(
        `${requestId}|${accessLinkToken ?? ""}`,
        () => getInformationRequestCarryForwards(requestId, accessLinkToken),
        refusal => informationRequestRefusalMessage(refusal, "Your previous answers could not be loaded."),
    );
    const carryForwards = read.value ?? [];

    if (read.error)
    {
        return (
            <InformationRequestLoadError id={"information-request-carry-forwards-load-error"}
                                         message={read.error}
                                         onRetry={() => void read.reload()}/>
        );
    }

    if (carryForwards.length === 0) return null;

    return (
        <section id={"information-request-carry-forwards"}
                 aria-labelledby={"information-request-carry-forwards-title"}
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
                    <Text id={`information-request-carry-forward-${carryForward.requirementId}-label`}
                          weight={"semibold"}>
                        {requirementLabels[carryForward.requirementId] ?? "Requested item"}
                    </Text>
                    <Badge id={`information-request-carry-forward-${carryForward.requirementId}-decision`}
                           appearance={"tint"}
                           color={carryForward.decision === InformationRequestCarryForwardDecision.OFFERED ? "brand" : "subtle"}>
                        {carryForwardLabels[carryForward.decision]}
                    </Badge>
                    <Text id={`information-request-carry-forward-${carryForward.requirementId}-detail`}
                          className={styles.detail}>
                        {carryForward.decision === InformationRequestCarryForwardDecision.OFFERED
                            ? priorAnswerText(carryForward, valueFormats[carryForward.requirementId])
                            : invalidationReasonLabels[carryForward.reasonCode ?? ""] ?? "This needs a new answer."}
                    </Text>
                </div>
            ))}
        </section>
    );
};

export default InformationRequestCarryForwardList;

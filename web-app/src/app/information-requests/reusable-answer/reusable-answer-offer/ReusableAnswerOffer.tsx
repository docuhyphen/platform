import {useState} from "react";
import {Button, Caption1, Checkbox, Text} from "@fluentui/react-components";
import {
    FieldOption,
    HiddenResponseClearTargetDto,
    InformationRequestAcceptedFactOfferDto
} from "../../../models/models.tsx";
import {hiddenClearTargetsOf} from "../../structured-response-workspace/hiddenResponseClearRefusal.ts";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {RecertifyReusableAnswer, ReusableAnswerResult} from "../ReusableAnswerContext.ts";
import {confidenceLabels, reusableValueText} from "../reusableAnswerLabels.ts";
import {useReusableAnswerOfferStyles} from "./ReusableAnswerOfferStyles.tsx";

interface Props
{
    elementId: string;
    requestId: string;
    requirementId: string;
    options?: FieldOption[];
    offer: InformationRequestAcceptedFactOfferDto;
    responseETag: string;
    busy: boolean;
    recertify: RecertifyReusableAnswer;
    onApplied: () => void;
    onResult: (result: ReusableAnswerResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const validityText = (offer: InformationRequestAcceptedFactOfferDto): string =>
{
    const ends = [offer.fact.validTo, offer.fact.expiresAt]
        .filter((value): value is number => value !== undefined && value !== null);
    return ends.length > 0 ? `Valid until ${formatInformationRequestTime(Math.min(...ends))}` : "No end date";
};

const ReusableAnswerOffer = ({
    elementId,
    requestId,
    requirementId,
                                 options = [],
    offer,
    responseETag,
    busy,
    recertify,
    onApplied,
    onResult,
    onCommandStart,
    onCommandFailure,
}: Props) =>
{
    const styles = useReusableAnswerOfferStyles();
    const [confirmed, setConfirmed] = useState(false);
    const [clearTargets, setClearTargets] = useState<HiddenResponseClearTargetDto[]>([]);
    const [confirmedClears, setConfirmedClears] = useState<string[]>([]);
    const id = `information-request-reusable-answer-${elementId}-OFFER`;

    const use = async () =>
    {
        onCommandStart();
        try
        {
            const result = clearTargets.length
                ? await recertify(requestId, offer.fact.id, requirementId, responseETag, confirmedClears)
                : await recertify(requestId, offer.fact.id, requirementId, responseETag);
            if (result.outcome === "SAVED")
            {
                setConfirmed(false);
                setClearTargets([]);
                setConfirmedClears([]);
                onApplied();
            }
            onResult(result);
        }
        catch (error: unknown)
        {
            const targets = hiddenClearTargetsOf(error);
            if (targets.length) {
                setClearTargets(targets);
                setConfirmedClears([]);
            }
            onCommandFailure(error);
        }
    };

    return (
        <div id={id}
             role={"group"}
             aria-label={"Earlier accepted answer"}
             className={styles.root}>
            <Text id={`${id}-title`}
                  weight={"semibold"}>
                An earlier accepted answer is available
            </Text>
            <Text id={`${id}-value`}
                  className={styles.value}>
                {reusableValueText(offer.fact.valueType, offer.fact.value, options)}
            </Text>
            <Caption1 id={`${id}-standing`}>
                {`${confidenceLabels[offer.fact.confidence]}. ${validityText(offer)}`}
            </Caption1>
            <div id={`${id}-actions`}
                 className={styles.actions}>
                <Checkbox id={`${id}-confirm`}
                          checked={confirmed}
                          disabled={busy}
                          label={"I confirm this answer is still accurate"}
                          onChange={(_, data) => setConfirmed(data.checked === true)}/>
                {clearTargets.map(target => (
                    <Checkbox key={target.requirementId}
                              id={`${id}-clear-${target.requirementId}`}
                              checked={confirmedClears.includes(target.requirementId)}
                              disabled={busy}
                              label={`Clear the answer at ${target.occurrencePath}`}
                              onChange={(_, data) => setConfirmedClears(previous => data.checked === true
                                  ? [...previous, target.requirementId]
                                  : previous.filter(value => value !== target.requirementId))}/>
                ))}
                <Button id={`${id}-use`}
                        appearance={"primary"}
                        shape={"circular"}
                        size={"small"}
                        disabled={!confirmed || busy || clearTargets.some(target => !confirmedClears.includes(target.requirementId))}
                        onClick={() => void use()}>
                    Use this answer
                </Button>
            </div>
        </div>
    );
};

export default ReusableAnswerOffer;

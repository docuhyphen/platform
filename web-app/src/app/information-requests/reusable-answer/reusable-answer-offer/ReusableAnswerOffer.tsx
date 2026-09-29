import {useState} from "react";
import {Button, Caption1, Checkbox, Text} from "@fluentui/react-components";
import {InformationRequestAcceptedFactOfferDto} from "../../../models/models.tsx";
import {formatInformationRequestTime} from "../../shared/informationRequestFormatting.ts";
import {RecertifyReusableAnswer, ReusableAnswerResult} from "../ReusableAnswerContext.ts";
import {confidenceLabels, reusableValueText} from "../reusableAnswerLabels.ts";
import {useReusableAnswerOfferStyles} from "./ReusableAnswerOfferStyles.tsx";

interface Props
{
    elementId: string;
    requestId: string;
    requirementId: string;
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
    const ends = [offer.fact.validTo, offer.fact.expiresAt].filter((value): value is string => Boolean(value)).sort()[0];
    return ends ? `Valid until ${formatInformationRequestTime(ends)}` : "No end date";
};

const ReusableAnswerOffer = ({
    elementId,
    requestId,
    requirementId,
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
    const id = `information-request-reusable-answer-${elementId}`;

    const use = async () =>
    {
        onCommandStart();
        try
        {
            const result = await recertify(requestId, offer.fact.id, requirementId, responseETag);
            if (result.outcome === "SAVED")
            {
                setConfirmed(false);
                onApplied();
            }
            onResult(result);
        }
        catch (error: unknown)
        {
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
                {reusableValueText(offer.fact.value)}
            </Text>
            <Caption1 id={`${id}-standing`}>
                {`${confidenceLabels[offer.fact.confidence]}. ${validityText(offer)}`}
            </Caption1>
            <div className={styles.actions}>
                <Checkbox id={`${id}-confirm`}
                          checked={confirmed}
                          disabled={busy}
                          label={"I confirm this answer is still accurate"}
                          onChange={(_, data) => setConfirmed(data.checked === true)}/>
                <Button id={`${id}-use`}
                        appearance={"primary"}
                        shape={"circular"}
                        size={"small"}
                        disabled={!confirmed || busy}
                        onClick={() => void use()}>
                    Use this answer
                </Button>
            </div>
        </div>
    );
};

export default ReusableAnswerOffer;

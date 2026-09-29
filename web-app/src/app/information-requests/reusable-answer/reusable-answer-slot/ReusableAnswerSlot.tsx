import {InformationRequestResponseDto} from "../../../models/models.tsx";
import {ReusableAnswerResult, useReusableAnswerSettings} from "../ReusableAnswerContext.ts";
import ReusableAnswerOffer from "../reusable-answer-offer/ReusableAnswerOffer.tsx";

interface Props
{
    elementId: string;
    requestId: string;
    response: InformationRequestResponseDto | undefined;
    shownValue: unknown;
    responseETag: string;
    busy: boolean;
    onApplied: () => void;
    onResult: (result: ReusableAnswerResult) => void;
    onCommandStart: () => void;
    onCommandFailure: (error: unknown) => void;
}

const ReusableAnswerSlot = ({response, shownValue, ...props}: Props) =>
{
    const settings = useReusableAnswerSettings();
    const offer = response && settings?.offers.find(candidate => candidate.requirementId === response.informationRequestRequirementId);
    if (!settings || !response || !offer) return null;
    if (JSON.stringify(shownValue ?? null) === JSON.stringify(offer.fact.value ?? null)) return null;

    return (
        <ReusableAnswerOffer {...props}
                             requirementId={response.informationRequestRequirementId}
                             offer={offer}
                             recertify={settings.recertify}/>
    );
};

export default ReusableAnswerSlot;

import {useEffect} from "react";
import {PlanFeature} from "../../models/models.tsx";
import {usePlanFeature} from "../../../hooks/subscription/usePlanFeature.ts";
import {ExchangeInitiationFormState} from "../exchangeInitiationTypes.ts";

const useExchangeInitiationFeatures = (form: ExchangeInitiationFormState) =>
{
    const blueprintAvailability = usePlanFeature(PlanFeature.BLUEPRINT_USE);
    const blueprintManagementAvailability = usePlanFeature(PlanFeature.BLUEPRINT_MANAGE);
    const documentLibraryAvailability = usePlanFeature(PlanFeature.DOCUMENT_LIBRARY_USE);
    const participantAvailability = usePlanFeature(PlanFeature.MULTIPLE_PARTICIPANTS);
    const businessFieldsAvailability = usePlanFeature(PlanFeature.BUSINESS_FIELDS_AND_SCHEMAS);
    const advancedAccessAvailability = usePlanFeature(PlanFeature.ADVANCED_ACCESS_CONTROLS);
    const {internalParticipants, setInternalParticipants} = form;

    useEffect(() =>
    {
        if (!participantAvailability.isAvailable && internalParticipants.length > 0)
        {
            setInternalParticipants([]);
        }
    }, [internalParticipants.length, participantAvailability.isAvailable, setInternalParticipants]);

    return {
        canUseBlueprints: blueprintAvailability.isAvailable,
        canManageBlueprints: blueprintManagementAvailability.isAvailable,
        canUseDocumentLibrary: documentLibraryAvailability.isAvailable,
        allowAdditionalParticipants: participantAvailability.isAvailable,
        canUseBusinessFields: businessFieldsAvailability.isAvailable,
        allowAdvancedAccessControls: advancedAccessAvailability.isAvailable,
    };
};

export type ExchangeInitiationFeatures = ReturnType<typeof useExchangeInitiationFeatures>;

export default useExchangeInitiationFeatures;

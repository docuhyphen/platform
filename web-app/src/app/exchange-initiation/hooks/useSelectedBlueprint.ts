import {useCallback, useState} from "react";
import {BlueprintDefinitionSummaryDto} from "../../models/models.tsx";
import {startsInformationRequest} from "./blueprintRequestStart.ts";

export interface SelectedBlueprint {
    id: string;
    name: string;
    informationRequestTemplateVersionId?: string;
}

interface Selection {
    blueprint: SelectedBlueprint;
    startsRequest: boolean;
}

const useSelectedBlueprint = () => {
    const [selection, setSelection] = useState<Selection | null>(null);

    const selectBlueprint = (blueprint: BlueprintDefinitionSummaryDto) =>
        setSelection({
            blueprint: {
                id: blueprint.id,
                name: blueprint.name,
                informationRequestTemplateVersionId: blueprint.informationRequestTemplateVersionId,
            },
            startsRequest: startsInformationRequest(blueprint),
        });

    const clearBlueprint = useCallback(() => setSelection(null), []);

    return {
        selectedBlueprint: selection?.blueprint ?? null,
        selectBlueprint,
        clearBlueprint,
        startsInformationRequest: selection?.startsRequest === true,
    };
};

export default useSelectedBlueprint;

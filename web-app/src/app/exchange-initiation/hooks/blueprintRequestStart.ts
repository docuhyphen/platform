import {BlueprintDefinitionSummaryDto, InformationRequestTemplateStatus} from "../../models/models.tsx";

export const pinnedVersionRetired = (blueprint: BlueprintDefinitionSummaryDto): boolean =>
    Boolean(blueprint.informationRequestTemplateVersionId) && (
        Boolean(blueprint.informationRequestTemplateVersion?.retiredAt)
        || blueprint.informationRequestTemplateVersion?.status === InformationRequestTemplateStatus.RETIRED
    );

export const startsInformationRequest = (blueprint: BlueprintDefinitionSummaryDto): boolean =>
    Boolean(blueprint.informationRequestTemplateVersionId) && !pinnedVersionRetired(blueprint);

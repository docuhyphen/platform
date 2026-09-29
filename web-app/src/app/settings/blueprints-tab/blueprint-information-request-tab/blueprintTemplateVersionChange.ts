import {UpdateBlueprintRequest} from "../../../models/models.tsx";

export type BlueprintTemplateVersionChange = Pick<UpdateBlueprintRequest, "informationRequestTemplateVersionId" | "clearInformationRequestTemplateVersion">;

export const templateVersionChange = (original: string | undefined, current: string | undefined): BlueprintTemplateVersionChange =>
{
    if (original === current) return {};
    return current ? {informationRequestTemplateVersionId: current} : {clearInformationRequestTemplateVersion: true};
};

import {
    BlueprintInformationRequestTemplateVersionDto,
    InformationRequestTemplateStatus
} from "../../../models/models.tsx";

export const pinnedVersionState = (pinned: BlueprintInformationRequestTemplateVersionDto): string | null => {
    if (pinned.status === InformationRequestTemplateStatus.RETIRED) return "Retired";
    if (!pinned.latest) return "Earlier version";
    return null;
};

export const pinnedVersionNotice = (pinned: BlueprintInformationRequestTemplateVersionDto): string | null => {
    if (pinned.status === InformationRequestTemplateStatus.RETIRED) {
        return `Version ${pinned.versionNumber} of ${pinned.templateDisplayName} was retired. ` +
            "New Exchanges cannot start this request until you choose a currently published Template.";
    }
    if (!pinned.latest && pinned.latestPublishedVersionNumber !== undefined && pinned.latestPublishedVersionNumber !== null) {
        return `Version ${pinned.latestPublishedVersionNumber} of ${pinned.templateDisplayName} is published. ` +
            `Exchanges keep using version ${pinned.versionNumber} until you choose the Template again.`;
    }
    return null;
};

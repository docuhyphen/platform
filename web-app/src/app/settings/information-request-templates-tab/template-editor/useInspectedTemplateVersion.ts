import {useState} from "react";
import {getInformationRequestTemplateVersion} from "../../../../services/informationRequestTemplateService.ts";
import {
    InformationRequestTemplateDto,
    InformationRequestTemplateStatus,
    InformationRequestTemplateVersionDto,
    InformationRequestTemplateVersionStandingDto,
} from "../../../models/models.tsx";
import {informationRequestRefusalMessage} from "../../../information-requests/shared/informationRequestRefusal.ts";
import {useServerRead} from "../../../information-requests/shared/useServerRead.ts";

const LOAD_FAILURE = "This Version could not be loaded.";

export interface InspectedTemplateVersion
{
    versionNumber: number | null;
    standing: InformationRequestTemplateVersionStandingDto | null;
    version: InformationRequestTemplateVersionDto | null;
    loading: boolean;
    error: string | null;
    hasCurrent: boolean;
    reload: () => Promise<void>;
    view: (versionNumber: number) => void;
    showCurrent: () => void;
}

interface PickedVersion
{
    templateId: string;
    versionNumber: number | null;
}

export const currentVersionNumberOf = (template: InformationRequestTemplateDto): number | null =>
    template.draftVersion?.versionNumber ?? template.latestPublishedVersion?.versionNumber ?? null;

const latestRetiredOf = (template: InformationRequestTemplateDto): number | null =>
    (template.versions ?? [])
        .filter(version => version.status === InformationRequestTemplateStatus.RETIRED)
        .reduce<number | null>(
            (latest, version) => latest === null || version.versionNumber > latest ? version.versionNumber : latest,
            null,
        );

export const useInspectedTemplateVersion = (template: InformationRequestTemplateDto): InspectedTemplateVersion =>
{
    const [picked, setPicked] = useState<PickedVersion | null>(null);
    const current = currentVersionNumberOf(template);
    const chosen = picked?.templateId === template.id ? picked.versionNumber : null;
    const versionNumber = chosen !== null && chosen !== current
        ? chosen
        : current === null ? latestRetiredOf(template) : null;
    const read = useServerRead<InformationRequestTemplateVersionDto | null>(
        `${template.id}:${versionNumber ?? "current"}`,
        () => versionNumber === null
            ? Promise.resolve(null)
            : getInformationRequestTemplateVersion(template.id, versionNumber),
        refusal => informationRequestRefusalMessage(refusal, LOAD_FAILURE),
    );
    const version = versionNumber === null ? null : read.value;
    const standing = versionNumber === null
        ? null
        : template.versions?.find(listed => listed.versionNumber === versionNumber)
        ?? {versionNumber, status: version?.status ?? InformationRequestTemplateStatus.PUBLISHED};

    return {
        versionNumber,
        standing,
        version,
        loading: versionNumber !== null && read.loading,
        error: versionNumber === null ? null : read.error,
        hasCurrent: current !== null,
        reload: read.reload,
        view: next => setPicked({templateId: template.id, versionNumber: next}),
        showCurrent: () => setPicked({templateId: template.id, versionNumber: null}),
    };
};

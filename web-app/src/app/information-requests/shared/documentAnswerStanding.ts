import {InformationRequestResponseDisposition, InformationRequestResponseDto} from "../../models/models.tsx";

const FILE_BACKED_DISPOSITIONS = new Set([
    InformationRequestResponseDisposition.NOT_ANSWERED,
    InformationRequestResponseDisposition.PROVIDED,
]);

export const attachedFiles = (response: InformationRequestResponseDto): number => response.evidenceFileCount ?? 0;

export const isFileBacked = (response: InformationRequestResponseDto): boolean =>
    FILE_BACKED_DISPOSITIONS.has(response.disposition);

export const documentAnswered = (response: InformationRequestResponseDto): boolean =>
    isFileBacked(response) ? attachedFiles(response) > 0 : true;

export const attachedFilesText = (files: number): string =>
    files === 1 ? "1 file attached" : `${files} files attached`;

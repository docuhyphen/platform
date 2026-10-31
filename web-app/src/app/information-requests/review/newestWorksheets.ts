import {InformationRequestReviewDto, InformationRequestReviewWorksheetDto} from "../../models/models.tsx";

const newer = (shown: InformationRequestReviewWorksheetDto | undefined, incoming: InformationRequestReviewWorksheetDto) =>
    shown && shown.draftRevision > incoming.draftRevision ? shown : incoming;

export const withNewestWorksheets = (read: InformationRequestReviewDto, shown: InformationRequestReviewDto | null): InformationRequestReviewDto =>
    shown
        ? {
            ...read,
            worksheets: read.worksheets.map(worksheet =>
                newer(shown.worksheets.find(candidate => candidate.assignmentId === worksheet.assignmentId), worksheet)),
        }
        : read;

export const withSavedWorksheet = (shown: InformationRequestReviewDto, saved: InformationRequestReviewWorksheetDto): InformationRequestReviewDto => ({
    ...shown,
    worksheets: shown.worksheets.map(worksheet => worksheet.assignmentId === saved.assignmentId ? newer(worksheet, saved) : worksheet),
});

import {ResponseError} from "../../models/models.tsx";

export const informationRequestRefusalMessage = (error: unknown, fallback: string): string =>
{
    if (typeof error === "string" && error.trim()) return error;
    if (error instanceof Error && error.message) return error.message;
    const refusal = error as ResponseError | undefined;
    return refusal?.errorMessage?.trim() ? refusal.errorMessage : fallback;
};

export const informationRequestRefusalCode = (error: unknown): string | undefined =>
    (error as ResponseError | undefined)?.reasonCode;

export const isStaleInformationRequestCommand = (error: unknown): boolean =>
{
    const code = informationRequestRefusalCode(error);
    return code === "COMMAND_PRECONDITION_STALE" || code === "FIELDS_PRECONDITION_STALE";
};

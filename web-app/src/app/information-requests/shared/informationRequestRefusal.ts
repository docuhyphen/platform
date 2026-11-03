import {ResponseError} from "../../models/models.tsx";

const readable = (message: unknown): message is string =>
    typeof message === "string" && message.trim().length > 0 && !/^\s*</.test(message);

export const informationRequestRefusalMessage = (error: unknown, fallback: string): string =>
{
    if (typeof error === "string") return readable(error) ? error : fallback;
    if (error instanceof Error) return readable(error.message) ? error.message : fallback;
    const stated = (error as ResponseError | undefined)?.errorMessage;
    return readable(stated) ? stated : fallback;
};

export const informationRequestRefusalCode = (error: unknown): string | undefined =>
    (error as ResponseError | undefined)?.reasonCode;

export const isInformationRequestForbidden = (error: unknown): boolean =>
    informationRequestRefusalCode(error) === "INFORMATION_REQUEST_FORBIDDEN";

export const isInformationRequestReadAccessLost = (error: unknown): boolean => {
    const candidate = informationRequestRefusalCode(error);
    const code = typeof candidate === "string" ? candidate : "";
    return isInformationRequestForbidden(error) || code.includes("NOT_FOUND") ||
        code.startsWith("INFORMATION_REQUEST_ACCESS_") || /^(AUTH|SESSION|TOKEN|MEMBERSHIP|ACCOUNT|ORGANIZATION)_/.test(code);
};

export const unlessForbidden = <T, >(read: Promise<T>): Promise<T | null> =>
    read.catch((refusal: unknown): null => {
        if (isInformationRequestForbidden(refusal)) return null;
        throw refusal;
    });

export const isStaleInformationRequestCommand = (error: unknown): boolean =>
{
    const code = informationRequestRefusalCode(error);
    return code === "COMMAND_PRECONDITION_STALE" || code === "FIELDS_PRECONDITION_STALE";
};

export const isRepeatedInformationRequestAccessLinkCommand = (error: unknown): boolean => {
    const code = informationRequestRefusalCode(error);
    return code === "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ISSUED" || code === "INFORMATION_REQUEST_ACCESS_LINK_ALREADY_ROTATED";
};

export const isInformationRequestEditLocked = (error: unknown): boolean => {
    const code = informationRequestRefusalCode(error);
    return code === "INFORMATION_REQUEST_SUBMISSION_LOCKED" || code === "INFORMATION_REQUEST_CORRECTION_SCOPE_DENIED";
};

export const isInformationRequestVerificationLapsed = (error: unknown): boolean =>
    informationRequestRefusalCode(error) === "INFORMATION_REQUEST_CONFIDENTIALITY_DENIED";

export const isInformationRequestRespondentChanged = (error: unknown): boolean =>
    informationRequestRefusalCode(error) === "INFORMATION_REQUEST_RESPONDENT_CHANGED";

export const isInformationRequestAccessSessionEnded = (error: unknown): boolean => {
    const code = informationRequestRefusalCode(error);
    return typeof code === "string" && code.startsWith("INFORMATION_REQUEST_ACCESS_SESSION_");
};

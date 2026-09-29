export const STALE_MESSAGE = "These answers changed somewhere else. Your changes are kept: review them against the latest answers, then choose Save responses.";

const GENERIC_COMMAND_FAILURE_MESSAGE = "The command could not be completed.";
const ACCESS_SESSION_CODE_PREFIX = "INFORMATION_REQUEST_ACCESS_SESSION_";

interface SaveStatusInput
{
    busy: boolean;
    stale: boolean;
    pending: boolean;
    saved: boolean;
}

export const saveStatusText = ({busy, stale, pending, saved}: SaveStatusInput): string =>
{
    if (busy) return "Saving your answers.";
    if (stale) return STALE_MESSAGE;
    if (pending) return "Unsaved changes. Saving shortly.";
    return saved ? "All changes saved." : "";
};

export const commandErrorMessage = (error: unknown): string =>
{
    if (typeof error === "string") return error;
    if (error instanceof Error) return error.message;
    if (typeof error !== "object" || error === null) return GENERIC_COMMAND_FAILURE_MESSAGE;
    const candidate = error as {errorMessage?: unknown; message?: unknown; reasonCode?: unknown};
    if (typeof candidate.errorMessage === "string" && candidate.errorMessage.trim()) return candidate.errorMessage;
    if (typeof candidate.message === "string" && candidate.message.trim()) return candidate.message;
    if (typeof candidate.reasonCode === "string" && candidate.reasonCode.trim()) return candidate.reasonCode;
    return GENERIC_COMMAND_FAILURE_MESSAGE;
};

export const isAccessSessionEnded = (error: unknown): boolean =>
{
    const reasonCode = (error as {reasonCode?: unknown} | null)?.reasonCode;
    return typeof reasonCode === "string" && reasonCode.startsWith(ACCESS_SESSION_CODE_PREFIX);
};

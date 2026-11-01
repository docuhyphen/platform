export enum AppUserEmailUpdateStage
{
    ENTER_EMAIL,
    CONFIRM_OLD,
    CONFIRM_NEW
}

export const extractAppUserEmailUpdateErrorMessage = (e: unknown): string =>
{
    if (typeof e === "string") return e;
    const error = e as { errorMessage?: string; message?: string; response?: { data?: { errorMessage?: string; message?: string } } };
    const msg = error.errorMessage || error.message || error.response?.data?.errorMessage || error.response?.data?.message;
    if (msg) return msg;
    return "Something went wrong. Please try again in a moment.";
};

export const appUserEmailUpdatePrimaryLabel = (stage: AppUserEmailUpdateStage): string =>
{
    if (stage === AppUserEmailUpdateStage.ENTER_EMAIL) return "Continue";
    if (stage === AppUserEmailUpdateStage.CONFIRM_OLD) return "Verify current email";
    return "Verify & update";
};

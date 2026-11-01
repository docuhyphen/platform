type DialogFailure = {
    errorMessage?: string;
    message?: string;
    response?: { data?: { errorMessage?: string; message?: string } };
};

export const readFailureMessage = (e: unknown): string | undefined =>
{
    const failure = e as DialogFailure;
    return failure.errorMessage
        || failure.message
        || failure.response?.data?.errorMessage
        || failure.response?.data?.message;
};

export const extractDialogErrorMessage = (e: unknown, fallback: string): string =>
{
    if (typeof e === "string") return e;
    return readFailureMessage(e) || fallback;
};

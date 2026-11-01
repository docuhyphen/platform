import validator from "validator";

export const extractPhoneManagementErrorMessage = (e: unknown): string =>
{
    if (typeof e === "string") return e;
    const error = e as { errorMessage?: string; message?: string; response?: { data?: { errorMessage?: string; message?: string } } };
    const msg = error.errorMessage || error.message || error.response?.data?.errorMessage || error.response?.data?.message;
    if (msg) return msg;
    return "Something went wrong. Please try again in a moment.";
};

export const validatePhone = (value: string | undefined): string | null =>
{
    if (!value || !value.trim()) return "Phone number is required";
    if (!validator.isMobilePhone(value.trim(), "any", {strictMode: false}))
    {
        return "Please enter a valid phone number";
    }
    return null;
};

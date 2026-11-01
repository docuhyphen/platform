import {isValidEmail} from "../../../utils/helpers.ts";

export const extractEmailManagementErrorMessage = (e: unknown): string =>
{
    if (typeof e === "string") return e;
    const error = e as { errorMessage?: string; message?: string; response?: { data?: { errorMessage?: string; message?: string } } };
    const msg = error.errorMessage || error.message || error.response?.data?.errorMessage || error.response?.data?.message;
    if (msg) return msg;
    return "Something went wrong. Please try again in a moment.";
};

export const validateEmail = (value: string | undefined): string | null =>
{
    if (!value || !value.trim()) return "Email is required";
    if (!isValidEmail(value.trim())) return "Please enter a valid email address";
    return null;
};

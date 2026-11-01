import {readFailureMessage} from "../../components/dialogErrorMessage.ts";

export const mapServerErrorMessage = (raw: string | undefined | null): string =>
{
    if (!raw) return "Something went wrong. Please try again in a moment.";
    const lower = raw.toLowerCase();
    if (lower.includes("already exists"))
    {
        return "A user with that email is already in this organization.";
    }
    if (lower.includes("valid email"))
    {
        return "Please enter a valid email address.";
    }
    if (lower.includes("permission"))
    {
        return "You do not have permission to add users.";
    }
    if (lower.includes("capacity") || lower.includes("limit"))
    {
        if (lower.includes("organization user limit reached") || lower.includes("limit is"))
        {
            return raw;
        }
        return "Your organization has reached its member limit. Upgrade your plan to add more users.";
    }
    return raw;
};

export const extractAddUserErrorMessage = (e: unknown): string =>
{
    if (typeof e === "string") return mapServerErrorMessage(e);
    return mapServerErrorMessage(readFailureMessage(e));
};

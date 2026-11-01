import {useRef, useState} from "react";
import type {ChangeEvent} from "react";
import {getApiBaseUrl} from "../../shared/apiBaseUrl.ts";

export type SalesFormState = {
    firstName: string;
    lastName: string;
    workEmail: string;
    phone: string;
    organizationName: string;
    organizationType: string;
    companySize: string;
    country: string;
    message: string;
    website: string;
};

export type SalesFormErrors = Partial<Record<keyof SalesFormState, string>>;

export type SubmitStatus = "idle" | "submitting" | "success" | "error";

export type SalesFieldChangeEvent = ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>;

export type SalesFormUpdate = (field: keyof SalesFormState) => (event: SalesFieldChangeEvent) => void;

export type SalesFormFieldsProps = {
    form: SalesFormState;
    errors: SalesFormErrors;
    update: SalesFormUpdate;
    isSubmitting: boolean;
};

const emptyForm: SalesFormState = {
    firstName: "",
    lastName: "",
    workEmail: "",
    phone: "",
    organizationName: "",
    organizationType: "",
    companySize: "",
    country: "",
    message: "",
    website: "",
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_SHORT_FIELD = 150;
const MAX_MESSAGE = 2000;
const MIN_SUBMIT_MILLIS = 2000;

const validate = (form: SalesFormState): SalesFormErrors =>
{
    const errors: SalesFormErrors = {};

    const requireShort = (field: keyof SalesFormState, label: string) =>
    {
        const value = form[field].trim();
        if (value === "") errors[field] = `${label} is required`;
        else if (value.length > MAX_SHORT_FIELD) errors[field] = `${label} is too long`;
    };

    requireShort("firstName", "First name");
    requireShort("lastName", "Last name");
    requireShort("organizationName", "Organization name");
    requireShort("organizationType", "Organization type");
    requireShort("companySize", "Company size");

    const email = form.workEmail.trim();
    if (email === "") errors.workEmail = "Work email is required";
    else if (!EMAIL_PATTERN.test(email)) errors.workEmail = "Enter a valid email address";

    if (form.message.trim().length > MAX_MESSAGE) errors.message = "Message is too long";

    return errors;
};

const readErrorMessage = async (response: Response): Promise<string> =>
{
    const data = await response.clone().json().catch(() => null) as {errorMessage?: string} | null;
    if (data?.errorMessage) return data.errorMessage;

    const text = await response.text().catch(() => "");
    return text || `Server error (${response.status}). Please try again.`;
};

export function useSpeakToSalesForm()
{
    const [form, setForm] = useState<SalesFormState>(emptyForm);
    const [errors, setErrors] = useState<SalesFormErrors>({});
    const [submitStatus, setSubmitStatus] = useState<SubmitStatus>("idle");
    const [errorMessage, setErrorMessage] = useState<string>("");
    const openedAtRef = useRef<number>(0);

    const update: SalesFormUpdate = (field) => (event) =>
    {
        const value = event.target.value;
        setForm((prev) => ({...prev, [field]: value}));
        setErrors((prev) => (prev[field] ? {...prev, [field]: undefined} : prev));
    };

    const isValid = Object.keys(validate(form)).length === 0;

    const markOpened = () =>
    {
        openedAtRef.current = Date.now();
    };

    const reset = () =>
    {
        setForm(emptyForm);
        setErrors({});
        setSubmitStatus("idle");
        setErrorMessage("");
        openedAtRef.current = Date.now();
    };

    const handleSubmit = async () =>
    {
        const validationErrors = validate(form);
        if (Object.keys(validationErrors).length > 0)
        {
            setErrors(validationErrors);
            return;
        }

        const submittedTooFast = Date.now() - openedAtRef.current < MIN_SUBMIT_MILLIS;
        if (form.website.trim() !== "" || submittedTooFast)
        {
            setSubmitStatus("success");
            return;
        }

        setSubmitStatus("submitting");
        setErrorMessage("");

        try
        {
            const response = await fetch(`${getApiBaseUrl()}/no-auth/sales-enquiries`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(form),
            });

            if (response.ok)
            {
                setSubmitStatus("success");
            }
            else
            {
                setErrorMessage(await readErrorMessage(response));
                setSubmitStatus("error");
            }
        }
        catch
        {
            setErrorMessage("Unable to reach the server. Please check your connection and try again.");
            setSubmitStatus("error");
        }
    };

    return {
        form,
        errors,
        submitStatus,
        errorMessage,
        isValid,
        update,
        handleSubmit,
        markOpened,
        reset,
        retry: () => setSubmitStatus("idle"),
    };
}

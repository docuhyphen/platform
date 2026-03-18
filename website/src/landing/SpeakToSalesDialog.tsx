import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Select,
    Spinner,
    Text,
    Textarea,
    makeStyles,
    tokens,
} from "@fluentui/react-components";
import {CheckmarkCircle24Regular, Dismiss24Regular, ErrorCircle24Regular, Send24Regular} from "@fluentui/react-icons";
import {useState} from "react";
import {BREAKPOINT_MOBILE, SPACE_MD, SPACE_SM} from "./shared.ts";

type FormState = {
    firstName: string;
    lastName: string;
    workEmail: string;
    phone: string;
    organizationName: string;
    organizationType: string;
    companySize: string;
    country: string;
    message: string;
};

type SubmitStatus = "idle" | "submitting" | "success" | "error";

const emptyForm: FormState = {
    firstName: "",
    lastName: "",
    workEmail: "",
    phone: "",
    organizationName: "",
    organizationType: "",
    companySize: "",
    country: "",
    message: "",
};

const useStyles = makeStyles({
    surface: {
        maxWidth: "40rem",
        width: "100%",
    },

    form: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
        marginTop: SPACE_SM,
    },

    row: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
        },
    },

    sectionLabel: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.06em",
        textTransform: "uppercase",
    },

    divider: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
    },

    feedback: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: SPACE_MD,
        padding: "2rem 0",
        textAlign: "center",
    },

    feedbackIcon: {
        fontSize: "3rem",
    },

    successIcon: {
        color: tokens.colorPaletteGreenForeground1,
    },

    errorIcon: {
        color: tokens.colorPaletteRedForeground1,
    },

    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: SPACE_SM,
    },
});

type SpeakToSalesDialogProps = {
    trigger: React.ReactElement;
};

export function SpeakToSalesDialog({trigger}: SpeakToSalesDialogProps)
{
    const styles = useStyles();
    const [form, setForm] = useState<FormState>(emptyForm);
    const [submitStatus, setSubmitStatus] = useState<SubmitStatus>("idle");
    const [errorMessage, setErrorMessage] = useState<string>("");
    const [open, setOpen] = useState(false);

    const update = (field: keyof FormState) => (
        e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
    ) => setForm((prev) => ({...prev, [field]: e.target.value}));

    const isValid =
        form.firstName.trim() !== "" &&
        form.lastName.trim() !== "" &&
        form.workEmail.trim() !== "" &&
        form.organizationName.trim() !== "" &&
        form.organizationType !== "" &&
        form.companySize !== "";

    const handleSubmit = async () => {
        if (!isValid) return;

        setSubmitStatus("submitting");
        setErrorMessage("");

        try
        {
            const response = await fetch("/api/sales-enquiries", {
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
                const text = await response.text();
                setErrorMessage(text || `Server error (${response.status}). Please try again.`);
                setSubmitStatus("error");
            }
        }
        catch
        {
            setErrorMessage("Unable to reach the server. Please check your connection and try again.");
            setSubmitStatus("error");
        }
    };

    const handleOpenChange = (_: unknown, data: {open: boolean}) => {
        setOpen(data.open);
        if (!data.open)
        {
            setTimeout(() => {
                setForm(emptyForm);
                setSubmitStatus("idle");
                setErrorMessage("");
            }, 300);
        }
    };

    const isSubmitting = submitStatus === "submitting";

    return (
        <Dialog open={open} onOpenChange={handleOpenChange}>
            <DialogTrigger disableButtonEnhancement>
                {trigger}
            </DialogTrigger>

            <DialogSurface className={styles.surface}>
                <DialogBody>
                    <DialogTitle
                        action={
                            <DialogTrigger action="close">
                                <Button
                                    appearance="subtle"
                                    aria-label="Close"
                                    icon={<Dismiss24Regular/>}
                                />
                            </DialogTrigger>
                        }
                    >
                        Speak to Sales
                    </DialogTitle>

                    <DialogContent>
                        {submitStatus === "success" && (
                            <div className={styles.feedback}>
                                <CheckmarkCircle24Regular className={`${styles.feedbackIcon} ${styles.successIcon}`}/>
                                <Text size={500} weight="semibold">Enquiry received — thank you!</Text>
                                <Text>
                                    Our sales team will review your details and be in touch shortly
                                    to arrange a personalised demo.
                                </Text>
                            </div>
                        )}

                        {submitStatus === "error" && (
                            <div className={styles.feedback}>
                                <ErrorCircle24Regular className={`${styles.feedbackIcon} ${styles.errorIcon}`}/>
                                <Text size={500} weight="semibold">Something went wrong</Text>
                                <Text>{errorMessage}</Text>
                                <Button
                                    appearance="outline"
                                    shape="circular"
                                    onClick={() => setSubmitStatus("idle")}
                                >
                                    Try again
                                </Button>
                            </div>
                        )}

                        {(submitStatus === "idle" || submitStatus === "submitting") && (
                            <div className={styles.form}>
                                <Text className={styles.sectionLabel}>Your details</Text>

                                <div className={styles.row}>
                                    <Field label="First name" required>
                                        <Input
                                            value={form.firstName}
                                            onChange={update("firstName")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                    <Field label="Last name" required>
                                        <Input
                                            value={form.lastName}
                                            onChange={update("lastName")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                </div>

                                <div className={styles.row}>
                                    <Field label="Work email" required>
                                        <Input
                                            type="email"
                                            value={form.workEmail}
                                            onChange={update("workEmail")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                    <Field label="Phone number">
                                        <Input
                                            type="tel"
                                            value={form.phone}
                                            onChange={update("phone")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                </div>

                                <div className={styles.divider}/>
                                <Text className={styles.sectionLabel}>Organization details</Text>

                                <div className={styles.row}>
                                    <Field label="Organization name" required>
                                        <Input
                                            value={form.organizationName}
                                            onChange={update("organizationName")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                    <Field label="Country / Region">
                                        <Input
                                            value={form.country}
                                            onChange={update("country")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                </div>

                                <div className={styles.row}>
                                    <Field label="Organization type" required>
                                        <Select
                                            value={form.organizationType}
                                            onChange={update("organizationType")}
                                            disabled={isSubmitting}
                                        >
                                            <option value="" disabled>Select a type</option>
                                            <option value="Banking / Lending">Banking / Lending</option>
                                            <option value="Healthcare / Medical">Healthcare / Medical</option>
                                            <option value="Accounting / Audit">Accounting / Audit</option>
                                            <option value="Real Estate / Property">Real Estate / Property</option>
                                            <option value="Legal / Law Firm">Legal / Law Firm</option>
                                            <option value="Insurance">Insurance</option>
                                            <option value="Technology">Technology</option>
                                            <option value="Other">Other</option>
                                        </Select>
                                    </Field>
                                    <Field label="Company size" required>
                                        <Select
                                            value={form.companySize}
                                            onChange={update("companySize")}
                                            disabled={isSubmitting}
                                        >
                                            <option value="" disabled>Select a size</option>
                                            <option value="1–10 employees">1–10 employees</option>
                                            <option value="11–50 employees">11–50 employees</option>
                                            <option value="51–200 employees">51–200 employees</option>
                                            <option value="201–1,000 employees">201–1,000 employees</option>
                                            <option value="1,000+ employees">1,000+ employees</option>
                                        </Select>
                                    </Field>
                                </div>

                                <div className={styles.divider}/>
                                <Text className={styles.sectionLabel}>How can we help?</Text>

                                <Field label="Message">
                                    <Textarea
                                        value={form.message}
                                        onChange={update("message")}
                                        resize="vertical"
                                        rows={4}
                                        disabled={isSubmitting}
                                    />
                                </Field>
                            </div>
                        )}
                    </DialogContent>

                    <DialogActions className={styles.actions}>
                        {submitStatus === "success" ? (
                            <DialogTrigger action="close">
                                <Button appearance="primary" shape="circular">Close</Button>
                            </DialogTrigger>
                        ) : submitStatus !== "error" && (
                            <>
                                <DialogTrigger action="close">
                                    <Button appearance="outline" shape="circular" disabled={isSubmitting}>
                                        Cancel
                                    </Button>
                                </DialogTrigger>
                                <Button
                                    appearance="primary"
                                    shape="circular"
                                    icon={isSubmitting ? <Spinner size="tiny"/> : <Send24Regular/>}
                                    iconPosition="after"
                                    disabled={!isValid || isSubmitting}
                                    onClick={handleSubmit}
                                >
                                    {isSubmitting ? "Sending..." : "Send enquiry"}
                                </Button>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}


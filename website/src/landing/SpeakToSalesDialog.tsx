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
import {useSpeakToSalesForm} from "./useSpeakToSalesForm.ts";

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

    // Honeypot: kept in the DOM and reachable by bots but hidden from real users and assistive
    // tech. Positioned offscreen instead of display:none so scripted fillers still see it.
    honeypot: {
        position: "absolute",
        width: "1px",
        height: "1px",
        overflow: "hidden",
        left: "-9999px",
        top: "auto",
    },
});

type SpeakToSalesDialogProps = {
    trigger: React.ReactElement;
};

export function SpeakToSalesDialog({trigger}: SpeakToSalesDialogProps)
{
    const styles = useStyles();
    const [open, setOpen] = useState(false);
    const {
        form,
        errors,
        submitStatus,
        errorMessage,
        isValid,
        update,
        handleSubmit,
        markOpened,
        reset,
        retry,
    } = useSpeakToSalesForm();

    const handleOpenChange = (_: unknown, data: {open: boolean}) => {
        setOpen(data.open);
        if (data.open)
        {
            markOpened();
        }
        else
        {
            setTimeout(reset, 300);
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
                                    id="speak-to-sales-header-close-btn"
                                    appearance="subtle"
                                    shape="circular"
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
                                <Text size={500} weight="semibold">Enquiry received, thank you!</Text>
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
                                    id="speak-to-sales-retry-btn"
                                    appearance="secondary"
                                    shape="circular"
                                    onClick={retry}
                                >
                                    Try again
                                </Button>
                            </div>
                        )}

                        {(submitStatus === "idle" || submitStatus === "submitting") && (
                            <div className={styles.form}>
                                <Text className={styles.sectionLabel}>Your details</Text>

                                <div className={styles.row}>
                                    <Field
                                        label="First name"
                                        required
                                        validationState={errors.firstName ? "error" : "none"}
                                        validationMessage={errors.firstName}
                                    >
                                        <Input
                                            id="speak-to-sales-first-name"
                                            value={form.firstName}
                                            onChange={update("firstName")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                    <Field
                                        label="Last name"
                                        required
                                        validationState={errors.lastName ? "error" : "none"}
                                        validationMessage={errors.lastName}
                                    >
                                        <Input
                                            id="speak-to-sales-last-name"
                                            value={form.lastName}
                                            onChange={update("lastName")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                </div>

                                <div className={styles.row}>
                                    <Field
                                        label="Work email"
                                        required
                                        validationState={errors.workEmail ? "error" : "none"}
                                        validationMessage={errors.workEmail}
                                    >
                                        <Input
                                            id="speak-to-sales-work-email"
                                            type="email"
                                            value={form.workEmail}
                                            onChange={update("workEmail")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                    <Field label="Phone number">
                                        <Input
                                            id="speak-to-sales-phone"
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
                                    <Field
                                        label="Organization name"
                                        required
                                        validationState={errors.organizationName ? "error" : "none"}
                                        validationMessage={errors.organizationName}
                                    >
                                        <Input
                                            id="speak-to-sales-organization-name"
                                            value={form.organizationName}
                                            onChange={update("organizationName")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                    <Field label="Country / Region">
                                        <Input
                                            id="speak-to-sales-country"
                                            value={form.country}
                                            onChange={update("country")}
                                            disabled={isSubmitting}
                                        />
                                    </Field>
                                </div>

                                <div className={styles.row}>
                                    <Field
                                        label="Organization type"
                                        required
                                        validationState={errors.organizationType ? "error" : "none"}
                                        validationMessage={errors.organizationType}
                                    >
                                        <Select
                                            id="speak-to-sales-organization-type"
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
                                    <Field
                                        label="Company size"
                                        required
                                        validationState={errors.companySize ? "error" : "none"}
                                        validationMessage={errors.companySize}
                                    >
                                        <Select
                                            id="speak-to-sales-company-size"
                                            value={form.companySize}
                                            onChange={update("companySize")}
                                            disabled={isSubmitting}
                                        >
                                            <option value="" disabled>Select a size</option>
                                            <option value="1-10 employees">1-10 employees</option>
                                            <option value="11-50 employees">11-50 employees</option>
                                            <option value="51-200 employees">51-200 employees</option>
                                            <option value="201-1,000 employees">201-1,000 employees</option>
                                            <option value="1,000+ employees">1,000+ employees</option>
                                        </Select>
                                    </Field>
                                </div>

                                <div className={styles.divider}/>
                                <Text className={styles.sectionLabel}>How can we help?</Text>

                                <Field
                                    label="Message"
                                    validationState={errors.message ? "error" : "none"}
                                    validationMessage={errors.message}
                                >
                                    <Textarea
                                        id="speak-to-sales-message"
                                        value={form.message}
                                        onChange={update("message")}
                                        resize="vertical"
                                        rows={4}
                                        disabled={isSubmitting}
                                    />
                                </Field>

                                <div className={styles.honeypot} aria-hidden="true">
                                    <label htmlFor="speak-to-sales-website">
                                        Do not fill this in
                                        <input
                                            id="speak-to-sales-website"
                                            type="text"
                                            tabIndex={-1}
                                            autoComplete="off"
                                            value={form.website}
                                            onChange={update("website")}
                                        />
                                    </label>
                                </div>
                            </div>
                        )}
                    </DialogContent>

                    <DialogActions className={styles.actions}>
                        {submitStatus === "success" ? (
                            <DialogTrigger action="close">
                                <Button
                                    id="speak-to-sales-close-btn"
                                    appearance="primary"
                                    shape="circular"
                                >
                                    Close
                                </Button>
                            </DialogTrigger>
                        ) : submitStatus !== "error" && (
                            <>
                                <Button
                                    id="speak-to-sales-submit-btn"
                                    appearance="primary"
                                    shape="circular"
                                    icon={isSubmitting ? <Spinner size="tiny"/> : <Send24Regular/>}
                                    iconPosition="after"
                                    disabled={!isValid || isSubmitting}
                                    onClick={handleSubmit}
                                >
                                    {isSubmitting ? "Sending..." : "Send enquiry"}
                                </Button>
                                <DialogTrigger action="close">
                                    <Button
                                        id="speak-to-sales-cancel-btn"
                                        appearance="secondary"
                                        shape="circular"
                                        disabled={isSubmitting}
                                    >
                                        Cancel
                                    </Button>
                                </DialogTrigger>
                            </>
                        )}
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}


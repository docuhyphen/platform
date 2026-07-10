import {
    Button,
    Text,
    makeStyles,
    tokens,
} from "@fluentui/react-components";
import {
    Dialog,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogActions,
} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {BREAKPOINT_MOBILE, SPACE_MD, SPACE_SM, SPACE_XS} from "./shared.ts";

const STORAGE_KEY = "dh_selected_industry";

export const INDUSTRY_OPTIONS = [
    {label: "Law Firms & Legal Practices", slug: "legal"},
    {label: "Real Estate & Property Management", slug: "real-estate"},
    {label: "Healthcare & Medical Practices", slug: "healthcare"},
    {label: "Accounting & Audit Firms", slug: "accounting"},
    {label: "Banks & Lending Institutions", slug: "banking"},
    {label: "Other", slug: "other"},
] as const;

export type IndustrySlug = (typeof INDUSTRY_OPTIONS)[number]["slug"];

export function getStoredIndustry(): IndustrySlug | null
{
    try
    {
        const value = localStorage.getItem(STORAGE_KEY);
        if (value && INDUSTRY_OPTIONS.some((o) => o.slug === value))
        {
            return value as IndustrySlug;
        }
    }
    catch
    { /* SSR / private browsing */
    }
    return null;
}

function setStoredIndustry(slug: IndustrySlug): void
{
    try
    {
        localStorage.setItem(STORAGE_KEY, slug);
    }
    catch
    { /* ignore */
    }
}

const useStyles = makeStyles({
    surface: {
        width: "100%",
        maxWidth: "30rem",
        borderRadius: "1rem",
        padding: "1.8rem",
        [BREAKPOINT_MOBILE]: {
            padding: "1.2rem",
        },
    },

    title: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorBrandForeground1,
    },

    subtitle: {
        color: tokens.colorNeutralForeground2,
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: SPACE_SM,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_XS,
        },
    },

    option: {
        justifyContent: "flex-start",
        textAlign: "left",
        minHeight: "2.8rem",
        borderRadius: "0.6rem",
        fontWeight: tokens.fontWeightRegular,
    },

    footer: {
        display: "flex",
        justifyContent: "flex-end",
        marginTop: SPACE_MD,
    },

    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: SPACE_SM,
        paddingTop: SPACE_MD,
    },
});

type IndustryPickerDialogProps = {
    onSelect: (slug: IndustrySlug) => void;
};

export function IndustryPickerDialog({onSelect}: IndustryPickerDialogProps)
{
    const styles = useStyles();
    const [selected, setSelected] = useState<IndustrySlug | null>(null);
    const [open, setOpen] = useState(false);

    // Delay open to after first paint so the portal mounts correctly
    useEffect(() =>
    {
        const id = requestAnimationFrame(() => setOpen(true));
        return () => cancelAnimationFrame(id);
    }, []);

    const handleConfirm = () =>
    {
        if (!selected) return;
        setStoredIndustry(selected);
        onSelect(selected);
    };

    const handleSkip = () =>
    {
        onSelect("other");
    };

    return (
        <Dialog open={open} modalType="alert">
            <DialogSurface className={styles.surface}>
                <DialogBody>
                    <DialogTitle className={styles.title}>
                        Hi, let us tailor our site to your organisation
                    </DialogTitle>
                    <DialogContent>
                        <Text className={styles.subtitle} block>
                            Select which industry you're in so we can show you the most relevant experience.
                        </Text>
                        <div className={styles.grid} style={{marginTop: SPACE_MD}}>
                            {INDUSTRY_OPTIONS.map((option) => (
                                <Button
                                    key={option.slug}
                                    appearance={selected === option.slug ? "primary" : "secondary"}
                                    className={styles.option}
                                    onClick={() => setSelected(option.slug)}
                                >
                                    {option.label}
                                </Button>
                            ))}
                        </div>
                    </DialogContent>
                    <DialogActions className={styles.actions}>
                        <Button
                            id="industry-picker-continue-btn"
                            appearance="primary"
                            shape="circular"
                            disabled={!selected}
                            onClick={handleConfirm}
                        >
                            Continue
                        </Button>
                        <Button
                            id="industry-picker-skip-btn"
                            appearance="subtle"
                            shape="circular"
                            onClick={handleSkip}
                        >
                            Skip
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}




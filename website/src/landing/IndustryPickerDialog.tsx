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
import {INDUSTRY_OPTIONS, setStoredIndustry} from "./industryOptions.ts";
import type {IndustrySlug} from "./industryOptions.ts";

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
        marginTop: SPACE_MD,

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
                        <div
                            id="industry-picker-options"
                            className={styles.grid}
                        >
                            {INDUSTRY_OPTIONS.map((option) => (
                                <Button
                                    id={`industry-picker-option-${option.slug}`}
                                    key={option.slug}
                                    appearance={selected === option.slug ? "primary" : "secondary"}
                                    className={styles.option}
                                    shape="circular"
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
                            appearance="secondary"
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




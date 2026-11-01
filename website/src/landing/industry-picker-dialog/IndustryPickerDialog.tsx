import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Text,
} from "@fluentui/react-components";
import {useEffect, useState} from "react";
import {INDUSTRY_OPTIONS, setStoredIndustry} from "../industryOptions.ts";
import type {IndustrySlug} from "../industryOptions.ts";
import {useIndustryPickerDialogStyles} from "./IndustryPickerDialogStyles.tsx";

type IndustryPickerDialogProps = {
    onSelect: (slug: IndustrySlug) => void;
};

export function IndustryPickerDialog({onSelect}: IndustryPickerDialogProps)
{
    const styles = useIndustryPickerDialogStyles();
    const [selected, setSelected] = useState<IndustrySlug | null>(null);
    const [open, setOpen] = useState(false);

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
        <Dialog open={open}
                modalType="alert">
            <DialogSurface id={"industry-picker-dialog-surface"}
                           className={styles.surface}>
                <DialogBody id={"industry-picker-dialog-body"}>
                    <DialogTitle id={"industry-picker-dialog-title"}
                                 className={styles.title}>
                        Hi, let us tailor our site to your organisation
                    </DialogTitle>
                    <DialogContent id={"industry-picker-dialog-content"}>
                        <Text id={"industry-picker-dialog-subtitle"}
                              className={styles.subtitle}
                              block>
                            Select which industry you're in so we can show you the most relevant experience.
                        </Text>
                        <div id={"industry-picker-options"}
                             className={styles.grid}>
                            {INDUSTRY_OPTIONS.map((option) => (
                                <Button id={`industry-picker-option-${option.slug}`}
                                        key={option.slug}
                                        appearance={selected === option.slug ? "primary" : "secondary"}
                                        className={styles.option}
                                        shape="circular"
                                        onClick={() => setSelected(option.slug)}>
                                    {option.label}
                                </Button>
                            ))}
                        </div>
                    </DialogContent>
                    <DialogActions id={"industry-picker-dialog-actions"}
                                   className={styles.actions}>
                        <Button id={"industry-picker-continue-btn"}
                                appearance="primary"
                                shape="circular"
                                disabled={!selected}
                                onClick={handleConfirm}>
                            Continue
                        </Button>
                        <Button id={"industry-picker-skip-btn"}
                                appearance="secondary"
                                shape="circular"
                                onClick={handleSkip}>
                            Skip
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
}

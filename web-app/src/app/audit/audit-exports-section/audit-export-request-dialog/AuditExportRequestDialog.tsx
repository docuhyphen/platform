import {useState} from "react";
import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Field,
    Input,
    Text,
    Textarea,
} from "@fluentui/react-components";
import {AuditExportCreateRequestDto} from "../../../models/models.tsx";
import {useAuditExportRequestDialogStyles} from "./AuditExportRequestDialogStyles.tsx";

interface AuditExportRequestDialogProps
{
    open: boolean;
    submitting: boolean;
    error: string | null;
    onDismiss: () => void;
    onSubmit: (request: AuditExportCreateRequestDto) => void;
}

/** Form dialog to request a new signed evidence export bundle. */
const AuditExportRequestDialog = (
    {
        open,
        submitting,
        error,
        onDismiss,
        onSubmit,
    }: AuditExportRequestDialogProps
) =>
{
    const styles = useAuditExportRequestDialogStyles();
    const [occurredAfter, setOccurredAfter] = useState<string>("");
    const [occurredBefore, setOccurredBefore] = useState<string>("");
    const [purpose, setPurpose] = useState<string>("");
    const [caseReference, setCaseReference] = useState<string>("");
    const [legalBasis, setLegalBasis] = useState<string>("");

    const canSubmit = occurredAfter !== "" && occurredBefore !== "" && purpose.trim() !== "";

    const submit = () =>
    {
        onSubmit({
            categories: [],
            occurredAfter: new Date(occurredAfter).toISOString(),
            occurredBefore: new Date(occurredBefore).toISOString(),
            purpose,
            caseReference: caseReference || undefined,
            legalBasis: legalBasis || undefined,
        });
    };

    return (
        <Dialog
            open={open}
            onOpenChange={(_, data) =>
            {
                if (!data.open)
                {
                    onDismiss();
                }
            }}
        >
            <DialogSurface id={"audit-export-request-surface"}>
                <DialogBody>
                    <DialogTitle>Request audit export</DialogTitle>
                    <DialogContent id={"audit-export-request-content"} className={styles.body}>
                        <div className={styles.row}>
                            <Field className={styles.formField} label={"Occurred after"} required>
                                <Input
                                    id={"audit-export-occurred-after"}
                                    type={"date"}
                                    value={occurredAfter}
                                    onChange={(_event, data) => setOccurredAfter(data.value)}
                                />
                            </Field>
                            <Field className={styles.formField} label={"Occurred before"} required>
                                <Input
                                    id={"audit-export-occurred-before"}
                                    type={"date"}
                                    value={occurredBefore}
                                    onChange={(_event, data) => setOccurredBefore(data.value)}
                                />
                            </Field>
                        </div>

                        <Field className={styles.formField} label={"Purpose"} required>
                            <Textarea
                                id={"audit-export-purpose"}
                                value={purpose}
                                onChange={(_event, data) => setPurpose(data.value)}
                            />
                        </Field>

                        <Field className={styles.formField} label={"Case reference"}>
                            <Input
                                id={"audit-export-case-reference"}
                                value={caseReference}
                                onChange={(_event, data) => setCaseReference(data.value)}
                            />
                        </Field>

                        <Field className={styles.formField} label={"Legal basis"}>
                            <Input
                                id={"audit-export-legal-basis"}
                                value={legalBasis}
                                onChange={(_event, data) => setLegalBasis(data.value)}
                            />
                        </Field>

                        {error && <Text className={styles.errorText}>{error}</Text>}
                    </DialogContent>
                    <DialogActions>
                        <Button
                            id={"button-audit-export-request-submit"}
                            appearance={"primary"}
                            shape={"circular"}
                            disabled={!canSubmit || submitting}
                            onClick={submit}
                        >
                            Request export
                        </Button>
                        <Button
                            id={"button-audit-export-request-cancel"}
                            appearance={"secondary"}
                            shape={"circular"}
                            onClick={onDismiss}
                        >
                            Cancel
                        </Button>
                    </DialogActions>
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default AuditExportRequestDialog;

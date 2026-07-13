import {useState} from "react";
import {
    Dialog,
    DialogBody,
    DialogSurface,
    DialogTitle,
} from "@fluentui/react-components";
import {AuditExportCreateRequestDto} from "../../../models/models.tsx";
import {dateOnlyToRangeEndInstant, dateOnlyToRangeStartInstant} from "../../auditDateRange.ts";
import AuditExportRequestActions from "./audit-export-request-actions/AuditExportRequestActions.tsx";
import AuditExportRequestFields from "./audit-export-request-fields/AuditExportRequestFields.tsx";

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
    const [occurredAfter, setOccurredAfter] = useState<string>("");
    const [occurredBefore, setOccurredBefore] = useState<string>("");
    const [categories, setCategories] = useState<string[]>([]);
    const [purpose, setPurpose] = useState<string>("");
    const [caseReference, setCaseReference] = useState<string>("");
    const [legalBasis, setLegalBasis] = useState<string>("");

    const canSubmit = categories.length > 0 && occurredAfter !== "" &&
        occurredBefore !== "" && purpose.trim() !== "";

    const submit = () =>
    {
        onSubmit({
            categories,
            occurredAfter: dateOnlyToRangeStartInstant(occurredAfter),
            occurredBefore: dateOnlyToRangeEndInstant(occurredBefore),
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
                <DialogBody id={"audit-export-request-body"}>
                    <DialogTitle id={"audit-export-request-title"}>Request audit export</DialogTitle>
                    <AuditExportRequestFields
                        categories={categories}
                        occurredAfter={occurredAfter}
                        occurredBefore={occurredBefore}
                        purpose={purpose}
                        caseReference={caseReference}
                        legalBasis={legalBasis}
                        error={error}
                        onCategoriesChange={setCategories}
                        onOccurredAfterChange={setOccurredAfter}
                        onOccurredBeforeChange={setOccurredBefore}
                        onPurposeChange={setPurpose}
                        onCaseReferenceChange={setCaseReference}
                        onLegalBasisChange={setLegalBasis}
                    />
                    <AuditExportRequestActions
                        disabled={!canSubmit || submitting}
                        onSubmit={submit}
                        onDismiss={onDismiss}
                    />
                </DialogBody>
            </DialogSurface>
        </Dialog>
    );
};

export default AuditExportRequestDialog;

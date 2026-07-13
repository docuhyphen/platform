import {DialogContent, Field, Input, Text, Textarea} from "@fluentui/react-components";
import AuditExportCategoriesField from "../audit-export-categories-field/AuditExportCategoriesField.tsx";
import {useAuditExportRequestFieldsStyles} from "./AuditExportRequestFieldsStyles.tsx";

interface AuditExportRequestFieldsProps
{
    categories: string[];
    occurredAfter: string;
    occurredBefore: string;
    purpose: string;
    caseReference: string;
    legalBasis: string;
    error: string | null;
    onCategoriesChange: (value: string[]) => void;
    onOccurredAfterChange: (value: string) => void;
    onOccurredBeforeChange: (value: string) => void;
    onPurposeChange: (value: string) => void;
    onCaseReferenceChange: (value: string) => void;
    onLegalBasisChange: (value: string) => void;
}

const AuditExportRequestFields = (props: AuditExportRequestFieldsProps) =>
{
    const styles = useAuditExportRequestFieldsStyles();
    return (
        <DialogContent
            id={"audit-export-request-content"}
            className={styles.body}
        >
            <AuditExportCategoriesField
                categories={props.categories}
                onChange={props.onCategoriesChange}
            />
            <div
                id={"audit-export-date-fields"}
                className={styles.row}
            >
                <Field
                    id={"audit-export-occurred-after-field"}
                    className={styles.field}
                    label={"Occurred after"}
                    required
                >
                    <Input
                        id={"audit-export-occurred-after"}
                        type={"date"}
                        value={props.occurredAfter}
                        onChange={(_event, data) => props.onOccurredAfterChange(data.value)}
                    />
                </Field>
                <Field
                    id={"audit-export-occurred-before-field"}
                    className={styles.field}
                    label={"Occurred before"}
                    required
                >
                    <Input
                        id={"audit-export-occurred-before"}
                        type={"date"}
                        value={props.occurredBefore}
                        onChange={(_event, data) => props.onOccurredBeforeChange(data.value)}
                    />
                </Field>
            </div>
            <Field
                id={"audit-export-purpose-field"}
                className={styles.field}
                label={"Purpose"}
                required
            >
                <Textarea
                    id={"audit-export-purpose"}
                    value={props.purpose}
                    onChange={(_event, data) => props.onPurposeChange(data.value)}
                />
            </Field>
            <Field
                id={"audit-export-case-reference-field"}
                className={styles.field}
                label={"Case reference"}
            >
                <Input
                    id={"audit-export-case-reference"}
                    value={props.caseReference}
                    onChange={(_event, data) => props.onCaseReferenceChange(data.value)}
                />
            </Field>
            <Field
                id={"audit-export-legal-basis-field"}
                className={styles.field}
                label={"Legal basis"}
            >
                <Input
                    id={"audit-export-legal-basis"}
                    value={props.legalBasis}
                    onChange={(_event, data) => props.onLegalBasisChange(data.value)}
                />
            </Field>
            {props.error && (
                <Text
                    id={"audit-export-request-error"}
                    className={styles.error}
                >{props.error}</Text>
            )}
        </DialogContent>
    );
};

export default AuditExportRequestFields;

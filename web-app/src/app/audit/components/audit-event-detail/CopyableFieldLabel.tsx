import {Button, Text, Tooltip} from "@fluentui/react-components";
import {CopyRegular} from "@fluentui/react-icons";
import {useAuditEventDetailStyles} from "./AuditEventDetailStyles.tsx";
import {useCopyableFieldLabelStyles} from "./CopyableFieldLabelStyles.tsx";

interface CopyableFieldLabelProps
{
    id: string;
    label: string;
    copyValue: string;
}

/**
 * A field label preceded by a small copy-to-clipboard icon button. Used where the visible field
 * value below intentionally omits a raw id (see AuditEventDetail's Actor/Target fields) so the id
 * is still reachable - copied alongside the display name, comma-separated - without ever being
 * shown as plain text.
 */
const CopyableFieldLabel = (
    {
        id,
        label,
        copyValue,
    }: CopyableFieldLabelProps
) =>
{
    const detailStyles = useAuditEventDetailStyles();
    const styles = useCopyableFieldLabelStyles();

    const copyWithDocumentFallback = () =>
    {
        const textArea = document.createElement("textarea");
        textArea.value = copyValue;
        textArea.setAttribute("readonly", "");
        textArea.className = styles.copyBuffer;
        document.body.appendChild(textArea);
        textArea.select();
        document.execCommand("copy");
        textArea.remove();
    };

    const copy = async () =>
    {
        try
        {
            if (!navigator.clipboard?.writeText)
            {
                copyWithDocumentFallback();
                return;
            }
            await navigator.clipboard.writeText(copyValue);
        }
        catch
        {
            copyWithDocumentFallback();
        }
    };

    return (
        <div className={styles.row}>
            <Tooltip
                content={`Copy ${label.toLowerCase()}`}
                relationship={"label"}>
                <Button
                    id={id}
                    appearance={"subtle"}
                    shape={"circular"}
                    size={"small"}
                    className={styles.copyButton}
                    icon={<CopyRegular/>}
                    aria-label={`Copy ${label.toLowerCase()}`}
                    onClick={() => void copy()}
                />
            </Tooltip>
            <Text
                id={`${id}-label`}
                size={200}
                className={detailStyles.fieldLabel}>
                {label}
            </Text>
        </div>
    );
};

export default CopyableFieldLabel;

import {Button, Text, Tooltip} from "@fluentui/react-components";
import {CopyIcon} from "../../../components/IconBundles.tsx";
import {copyText} from "../../../utils/copyText.ts";
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

    return (
        <div className={styles.row}>
            <Tooltip content={"Copy name and id"} relationship={"label"}>
                <Button
                    id={id}
                    appearance={"subtle"}
                    shape={"circular"}
                    size={"small"}
                    className={styles.copyButton}
                    icon={<CopyIcon/>}
                    onClick={() => void copyText(copyValue)}
                />
            </Tooltip>
            <Text size={200} className={detailStyles.fieldLabel}>{label}</Text>
        </div>
    );
};

export default CopyableFieldLabel;

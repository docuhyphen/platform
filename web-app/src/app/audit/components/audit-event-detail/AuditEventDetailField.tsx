import {Text} from "@fluentui/react-components";
import {useAuditEventDetailStyles} from "./AuditEventDetailStyles.tsx";

interface AuditEventDetailFieldProps
{
    label: string;
    value: string;
}

/** A plain label/value pair in AuditEventDetail's field grid (no copy affordance). */
const AuditEventDetailField = (
    {
        label,
        value,
    }: AuditEventDetailFieldProps
) =>
{
    const styles = useAuditEventDetailStyles();

    return (
        <div className={styles.field}>
            <Text size={200} className={styles.fieldLabel}>{label}</Text>
            <Text className={styles.fieldValue}>{value}</Text>
        </div>
    );
};

export default AuditEventDetailField;

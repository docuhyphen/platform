import {Text} from "@fluentui/react-components";
import {useAuditEventDetailStyles} from "../AuditEventDetailStyles.tsx";
import {useAuditEventDetailFieldStyles} from "./AuditEventDetailFieldStyles.tsx";

interface AuditEventDetailFieldProps
{
    label: string;
    value: string;
}

const AuditEventDetailField = (
    {
        label,
        value,
    }: AuditEventDetailFieldProps
) =>
{
    const detailStyles = useAuditEventDetailStyles();
    const styles = useAuditEventDetailFieldStyles();

    return (
        <div id={"audit-event-detail-field-div-1"}
             className={styles.field}>
            <Text id={"audit-event-detail-field-text-1"}
                  size={200}
                  className={detailStyles.fieldLabel}>{label}</Text>
            <Text id={"audit-event-detail-field-text-2"}
                  className={detailStyles.fieldValue}>{value}</Text>
        </div>
    );
};

export default AuditEventDetailField;

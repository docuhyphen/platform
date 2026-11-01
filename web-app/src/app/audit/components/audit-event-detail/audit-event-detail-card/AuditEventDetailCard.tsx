import {ReactElement} from "react";
import {Text} from "@fluentui/react-components";
import {useAuditEventDetailStyles} from "../AuditEventDetailStyles.tsx";
import {useAuditEventDetailCardStyles} from "./AuditEventDetailCardStyles.tsx";
import CopyableFieldLabel from "../copyable-field-label/CopyableFieldLabel.tsx";

interface AuditEventDetailCardProps
{
    id: string;
    icon: ReactElement;
    label: string;
    value: string;
    copyValue?: string;
}

const AuditEventDetailCard = (
    {
        id,
        icon,
        label,
        value,
        copyValue,
    }: AuditEventDetailCardProps
) =>
{
    const detailStyles = useAuditEventDetailStyles();
    const styles = useAuditEventDetailCardStyles();

    return (
        <div id={id}
             className={styles.card}>
            <span id={"audit-event-detail-card-span-1"}
                  className={styles.cardIcon}>{icon}</span>
            <div id={"audit-event-detail-card-div-1"}
                 className={styles.cardBody}>
                {copyValue !== undefined
                    ? (
                        <CopyableFieldLabel
                            id={`button-${id}-copy`}
                            label={label}
                            copyValue={copyValue}
                        />
                    )
                    : (
                        <Text id={"audit-event-detail-card-text-1"}
                              size={200}
                              className={detailStyles.fieldLabel}>{label}</Text>
                    )}
                <Text id={"audit-event-detail-card-text-2"}
                      className={detailStyles.fieldValue}>{value}</Text>
            </div>
        </div>
    );
};

export default AuditEventDetailCard;

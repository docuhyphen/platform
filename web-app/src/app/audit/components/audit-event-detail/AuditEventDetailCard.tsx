import {ReactElement} from "react";
import {Text} from "@fluentui/react-components";
import {useAuditEventDetailStyles} from "./AuditEventDetailStyles.tsx";
import CopyableFieldLabel from "./CopyableFieldLabel.tsx";

interface AuditEventDetailCardProps
{
    id: string;
    icon: ReactElement;
    label: string;
    value: string;
    /** When set, the label is preceded by a copy-to-clipboard button (see CopyableFieldLabel) instead of plain text. */
    copyValue?: string;
}

/** One full-width, icon-led card in AuditEventDetail's primary field list (Event type, Occurred at, Actor, Target, Reason). */
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
    const styles = useAuditEventDetailStyles();

    return (
        <div id={id} className={styles.card}>
            <span className={styles.cardIcon}>{icon}</span>
            <div className={styles.cardBody}>
                {copyValue !== undefined
                    ? (
                        <CopyableFieldLabel
                            id={`button-${id}-copy`}
                            label={label}
                            copyValue={copyValue}
                        />
                    )
                    : (
                        <Text size={200} className={styles.fieldLabel}>{label}</Text>
                    )}
                <Text className={styles.fieldValue}>{value}</Text>
            </div>
        </div>
    );
};

export default AuditEventDetailCard;

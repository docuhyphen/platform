import { Badge, Text } from "@fluentui/react-components";
import { ExchangeClearanceStatusDto } from "../../../models/models.tsx";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";

const BADGE_APPEARANCE: Record<string, "filled" | "ghost" | "outline" | "tint"> = {
    NONE: "ghost",
    RUNNING: "tint",
    CLEARED: "filled",
    BLOCKED: "filled",
};

const BADGE_COLOR: Record<string, "neutral" | "informative" | "success" | "important" | "severe" | "warning" | "subtle" | "brand"> = {
    NONE: "neutral",
    RUNNING: "informative",
    CLEARED: "success",
    BLOCKED: "important",
};

const formatClearanceStatus = (status: string) =>
{
    const label = status.toLowerCase();
    return label.charAt(0).toUpperCase() + label.slice(1);
};

interface Props
{
    clearance: ExchangeClearanceStatusDto;
}

const ClearanceStatusCard = ({ clearance }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const myStatus = clearance.myOrg.status;

    if (myStatus === "NONE" && clearance.counterparties.every(c => c.status === "NONE")) return null;

    return (
        <div id="clearance-status-card"
             className={styles.clearanceCard}>
            <Text id="clearance-status-label"
                  weight="semibold"
                  size={300}>
                Internal Clearance Status
            </Text>
            <div id="clearance-status-badges"
                 className={styles.clearanceBadgeRow}>
                {myStatus !== "NONE" && (
                    <Badge id="clearance-badge-my-org"
                           appearance={BADGE_APPEARANCE[myStatus] ?? "ghost"}
                           color={BADGE_COLOR[myStatus] ?? "neutral"}>
                        My Organization: {formatClearanceStatus(myStatus)}
                    </Badge>
                )}
                {clearance.counterparties.map((cp, i) =>
                    cp.status !== "NONE" && (
                        <Badge id={`clearance-badge-counterparty-${i}`}
                               key={i}
                               appearance={BADGE_APPEARANCE[cp.status] ?? "ghost"}
                               color={BADGE_COLOR[cp.status] ?? "neutral"}>
                            Counterparty: {formatClearanceStatus(cp.status)}
                        </Badge>
                    )
                )}
            </div>
        </div>
    );
};

export default ClearanceStatusCard;

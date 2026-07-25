import React, {useState} from "react";
import {Button, mergeClasses, Text} from "@fluentui/react-components";
import {NoAuthExchangeBasicDto} from "../../../models/models.tsx";
import {
    CheckmarkIcon,
    DocumentsIcon,
    ManageAccessIcon,
    ToggleHeaderDownIcon,
    ToggleHeaderUpIcon,
} from "../../../components/IconBundles.tsx";
import {useNoAuthExchangeOverviewStyles} from "./NoAuthExchangeOverviewStyles.tsx";

interface NoAuthExchangeOverviewProps
{
    exchange: NoAuthExchangeBasicDto;
}

const NoAuthExchangeOverview: React.FC<NoAuthExchangeOverviewProps> = ({exchange}) =>
{
    const styles = useNoAuthExchangeOverviewStyles();
    const [collapsed, setCollapsed] = useState(false);
    const requesterName = [exchange.initiatorFirstName, exchange.initiatorLastName].filter(Boolean).join(" ");
    const accessWindowDays = Math.max(1, exchange.noAuthAccessValidityDays ?? 7);

    return (
        <aside
            id={"no-auth-exchange-overview"}
            className={mergeClasses(styles.container, collapsed && styles.collapsedContainer)}
        >
            <div className={styles.summaryHeader}>
                <div className={styles.eyebrow}>
                    <ManageAccessIcon/>
                    <Text weight={"semibold"}>Secure Exchange details</Text>
                </div>
                <Button
                    id={"no-auth-exchange-overview-toggle"}
                    appearance={"subtle"}
                    shape={"circular"}
                    size={"small"}
                    icon={collapsed ? <ToggleHeaderDownIcon/> : <ToggleHeaderUpIcon/>}
                    aria-label={collapsed ? "Show exchange details" : "Hide exchange details"}
                    aria-expanded={!collapsed}
                    onClick={() => setCollapsed((current) => !current)}
                />
            </div>
            {!collapsed && (
                <div className={styles.details}>
                    <div className={styles.heading}>
                        <Text className={styles.requestedBy}>Shared by {requesterName || "your contact"}</Text>
                        <Text
                            size={700}
                            weight={"semibold"}
                            className={styles.exchangeName}
                        >
                            {exchange.name}
                        </Text>
                        {exchange.recipientOrganizationName && (
                            <Text className={styles.recipientOrganization}>
                                For {exchange.recipientOrganizationName}
                            </Text>
                        )}
                    </div>
                    {exchange.initialShareMessage && (
                        <div className={styles.message}>
                            <Text className={styles.messageLabel}>Exchange message</Text>
                            <Text className={styles.messageText}>{exchange.initialShareMessage}</Text>
                        </div>
                    )}
                    <div className={styles.assuranceList}>
                        <div className={styles.assuranceItem}>
                            <ManageAccessIcon className={styles.assuranceIcon}/>
                            <div className={styles.assuranceText}>
                                <Text weight={"semibold"}>Access protected</Text>
                                <Text size={300}>This private link is tied to your verified access.</Text>
                            </div>
                        </div>
                        <div className={styles.assuranceItem}>
                            <DocumentsIcon className={styles.assuranceIcon}/>
                            <div className={styles.assuranceText}>
                                <Text weight={"semibold"}>Files stay with this Exchange</Text>
                                <Text size={300}>Use this workspace to view or provide exchange documents.</Text>
                            </div>
                        </div>
                        <div className={styles.assuranceItem}>
                            <CheckmarkIcon className={styles.assuranceIcon}/>
                            <div className={styles.assuranceText}>
                                <Text weight={"semibold"}>{accessWindowDays}-day verified access</Text>
                                <Text size={300}>Return while your verified access remains valid.</Text>
                            </div>
                        </div>
                    </div>
                    <Text className={styles.footerNote}>
                        You do not need a DocuHyphen account to use this Exchange.
                    </Text>
                </div>
            )}
        </aside>
    );
};

export default NoAuthExchangeOverview;

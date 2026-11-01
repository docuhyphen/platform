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
            <div id={"no-auth-exchange-overview-div-1"}
                 className={styles.summaryHeader}>
                <div id={"no-auth-exchange-overview-div-2"}
                     className={styles.eyebrow}>
                    <ManageAccessIcon/>
                    <Text id={"no-auth-exchange-overview-text-1"}
                          weight={"semibold"}>Secure Exchange details</Text>
                </div>
                <Button
                    id={"no-auth-exchange-overview-toggle"}
                    appearance={"subtle"}
                    shape={"circular"}
                    size={"small"}
                    icon={collapsed ? <ToggleHeaderDownIcon/> : <ToggleHeaderUpIcon/>}
                    aria-label={collapsed ? "Show Exchange details" : "Hide Exchange details"}
                    aria-expanded={!collapsed}
                    onClick={() => setCollapsed((current) => !current)}
                />
            </div>
            {!collapsed && (
                <div id={"no-auth-exchange-overview-div-3"}
                     className={styles.details}>
                    <div id={"no-auth-exchange-overview-div-4"}
                         className={styles.heading}>
                        <Text id={"no-auth-exchange-overview-text-2"}
                              className={styles.requestedBy}>Shared by {requesterName || "your contact"}</Text>
                        <Text
                            id={"no-auth-exchange-overview-text-3"}
                            size={700}
                            weight={"semibold"}
                            className={styles.exchangeName}
                        >
                            {exchange.name}
                        </Text>
                        {exchange.recipientOrganizationName && (
                            <Text id={"no-auth-exchange-overview-text-4"}
                                  className={styles.recipientOrganization}>
                                For {exchange.recipientOrganizationName}
                            </Text>
                        )}
                    </div>
                    {exchange.initialShareMessage && (
                        <div id={"no-auth-exchange-overview-div-5"}
                             className={styles.message}>
                            <Text id={"no-auth-exchange-overview-text-5"}
                                  className={styles.messageLabel}>Exchange message</Text>
                            <Text id={"no-auth-exchange-overview-text-6"}
                                  className={styles.messageText}>{exchange.initialShareMessage}</Text>
                        </div>
                    )}
                    <div id={"no-auth-exchange-overview-div-6"}
                         className={styles.assuranceList}>
                        <div id={"no-auth-exchange-overview-div-7"}
                             className={styles.assuranceItem}>
                            <ManageAccessIcon className={styles.assuranceIcon}/>
                            <div id={"no-auth-exchange-overview-div-8"}
                                 className={styles.assuranceText}>
                                <Text id={"no-auth-exchange-overview-text-7"}
                                      weight={"semibold"}>Access protected</Text>
                                <Text id={"no-auth-exchange-overview-text-8"}
                                      size={300}>This private link is tied to your verified access.</Text>
                            </div>
                        </div>
                        <div id={"no-auth-exchange-overview-div-9"}
                             className={styles.assuranceItem}>
                            <DocumentsIcon className={styles.assuranceIcon}/>
                            <div id={"no-auth-exchange-overview-div-10"}
                                 className={styles.assuranceText}>
                                <Text id={"no-auth-exchange-overview-text-9"}
                                      weight={"semibold"}>Files stay with this Exchange</Text>
                                <Text id={"no-auth-exchange-overview-text-10"}
                                      size={300}>Use this workspace to view or provide Exchange documents.</Text>
                            </div>
                        </div>
                        <div id={"no-auth-exchange-overview-div-11"}
                             className={styles.assuranceItem}>
                            <CheckmarkIcon className={styles.assuranceIcon}/>
                            <div id={"no-auth-exchange-overview-div-12"}
                                 className={styles.assuranceText}>
                                <Text id={"no-auth-exchange-overview-text-11"}
                                      weight={"semibold"}>{accessWindowDays}-day verified access</Text>
                                <Text id={"no-auth-exchange-overview-text-12"}
                                      size={300}>Return while your verified access remains valid.</Text>
                            </div>
                        </div>
                    </div>
                    <Text id={"no-auth-exchange-overview-text-13"}
                          className={styles.footerNote}>
                        You do not need a DocuHyphen account to use this Exchange.
                    </Text>
                </div>
            )}
        </aside>
    );
};

export default NoAuthExchangeOverview;

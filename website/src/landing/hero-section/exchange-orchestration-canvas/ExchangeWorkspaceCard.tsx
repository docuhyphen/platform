import {Text, mergeClasses} from "@fluentui/react-components";
import {
    BranchFork24Regular,
    CheckmarkCircle24Filled,
    DocumentText24Regular,
    PeopleTeam24Regular,
    ShieldLock24Regular,
} from "@fluentui/react-icons";
import {useExchangeOrchestrationCanvasStyles} from "./ExchangeOrchestrationCanvasStyles.tsx";

export function ExchangeWorkspaceCard()
{
    const styles = useExchangeOrchestrationCanvasStyles();

    return (
        <article
            id="orchestration-exchange-card"
            className={styles.exchangeCard}
        >
            <div
                id="orchestration-exchange-card-header"
                className={styles.cardHeader}
            >
                <span
                    id="orchestration-exchange-icon"
                    className={styles.exchangeIcon}
                >
                    <ShieldLock24Regular id={"exchange-workspace-card-shield-lock24-regular-1"}
                                         aria-hidden="true"/>
                </span>
                <span id="orchestration-exchange-heading">
                    <Text
                        id="orchestration-exchange-label"
                        className={styles.cardEyebrow}
                    >
                        Exchange
                    </Text>
                    <Text
                        id="orchestration-exchange-title"
                        className={styles.cardTitle}
                        weight="semibold"
                    >
                        Business process
                    </Text>
                </span>
                <span
                    id="orchestration-exchange-status"
                    className={styles.activeBadge}
                >
                    Active
                </span>
            </div>

            <div
                id="orchestration-exchange-summary"
                className={styles.summary}
            >
                <Text id={"exchange-workspace-card-text-1"}
                      size={200}>4 participants</Text>
                <Text id={"exchange-workspace-card-text-2"}
                      size={200}>8 documents</Text>
                <Text id={"exchange-workspace-card-text-3"}
                      size={200}>2 approvals</Text>
            </div>

            <div
                id="orchestration-exchange-activity"
                className={styles.activityList}
            >
                <div
                    id="orchestration-activity-access"
                    className={styles.activityRow}
                >
                    <PeopleTeam24Regular id={"exchange-workspace-card-people-team24-regular-1"}
                                         aria-hidden="true"/>
                    <Text id={"exchange-workspace-card-text-4"}
                          className={styles.activityLabel}>Participant access</Text>
                    <CheckmarkCircle24Filled id={"exchange-workspace-card-checkmark-circle24-filled-1"}
                                             className={styles.completeIcon}/>
                </div>
                <div
                    id="orchestration-activity-request"
                    className={styles.activityRow}
                >
                    <DocumentText24Regular id={"exchange-workspace-card-document-text24-regular-1"}
                                           aria-hidden="true"/>
                    <Text id={"exchange-workspace-card-text-5"}
                          className={styles.activityLabel}>Document requests</Text>
                    <Text id={"exchange-workspace-card-text-6"}
                          className={styles.activityValue}>6 of 8</Text>
                </div>
                <div
                    id="orchestration-activity-workflow"
                    className={mergeClasses(styles.activityRow, styles.activeActivityRow)}
                >
                    <BranchFork24Regular id={"exchange-workspace-card-branch-fork24-regular-1"}
                                         aria-hidden="true"/>
                    <Text id={"exchange-workspace-card-text-7"}
                          className={styles.activityLabel}>Approval workflow</Text>
                    <span
                        id="orchestration-activity-live-status"
                        className={styles.liveStatus}
                    >
                        In review
                    </span>
                </div>
            </div>
        </article>
    );
}

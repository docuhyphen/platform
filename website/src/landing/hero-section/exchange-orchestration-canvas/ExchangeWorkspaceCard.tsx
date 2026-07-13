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
                    <ShieldLock24Regular aria-hidden="true"/>
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
                <Text size={200}>4 participants</Text>
                <Text size={200}>8 documents</Text>
                <Text size={200}>2 approvals</Text>
            </div>

            <div
                id="orchestration-exchange-activity"
                className={styles.activityList}
            >
                <div
                    id="orchestration-activity-access"
                    className={styles.activityRow}
                >
                    <PeopleTeam24Regular aria-hidden="true"/>
                    <Text className={styles.activityLabel}>Participant access</Text>
                    <CheckmarkCircle24Filled className={styles.completeIcon}/>
                </div>
                <div
                    id="orchestration-activity-request"
                    className={styles.activityRow}
                >
                    <DocumentText24Regular aria-hidden="true"/>
                    <Text className={styles.activityLabel}>Document requests</Text>
                    <Text className={styles.activityValue}>6 of 8</Text>
                </div>
                <div
                    id="orchestration-activity-workflow"
                    className={mergeClasses(styles.activityRow, styles.activeActivityRow)}
                >
                    <BranchFork24Regular aria-hidden="true"/>
                    <Text className={styles.activityLabel}>Approval workflow</Text>
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

import {mergeClasses} from "@fluentui/react-components";
import {
    BranchFork24Regular,
    CheckmarkCircle24Filled,
    DocumentText24Regular,
    History24Regular,
    MailAlert24Regular,
    PeopleTeam24Regular,
    PlugConnected24Regular,
} from "@fluentui/react-icons";
import {ExchangeWorkspaceCard} from "./ExchangeWorkspaceCard.tsx";
import {OrchestrationNode} from "./OrchestrationNode.tsx";
import {OrchestrationWires} from "./OrchestrationWires.tsx";
import {useExchangeOrchestrationCanvasStyles} from "./ExchangeOrchestrationCanvasStyles.tsx";

export function ExchangeOrchestrationCanvas()
{
    const styles = useExchangeOrchestrationCanvasStyles();

    return (
        <div
            id="exchange-orchestration-canvas"
            className={styles.canvas}
            aria-label="An Exchange coordinating people, documents, workflows, notifications, audit history, and integrations"
        >
            <OrchestrationWires/>

            <OrchestrationNode
                id="orchestration-node-participants"
                label="Participants"
                className={mergeClasses(styles.node, styles.nodeParticipants)}
                icon={<PeopleTeam24Regular id={"exchange-orchestration-canvas-people-team24-regular-1"}
                                           aria-hidden="true"/>}
            />
            <OrchestrationNode
                id="orchestration-node-documents"
                label="Documents"
                className={mergeClasses(styles.node, styles.nodeDocuments)}
                icon={<DocumentText24Regular id={"exchange-orchestration-canvas-document-text24-regular-1"}
                                             aria-hidden="true"/>}
            />
            <OrchestrationNode
                id="orchestration-node-workflows"
                label="Workflows"
                className={mergeClasses(styles.node, styles.nodeWorkflows)}
                icon={<BranchFork24Regular id={"exchange-orchestration-canvas-branch-fork24-regular-1"}
                                           aria-hidden="true"/>}
            />
            <OrchestrationNode
                id="orchestration-node-notifications"
                label="Notifications"
                className={mergeClasses(styles.node, styles.nodeNotifications)}
                icon={<MailAlert24Regular id={"exchange-orchestration-canvas-mail-alert24-regular-1"}
                                          aria-hidden="true"/>}
            />
            <OrchestrationNode
                id="orchestration-node-audit"
                label="Audit trail"
                className={mergeClasses(styles.node, styles.nodeAudit)}
                icon={<History24Regular id={"exchange-orchestration-canvas-history24-regular-1"}
                                        aria-hidden="true"/>}
            />
            <OrchestrationNode
                id="orchestration-node-integrations"
                label="Integrations"
                className={mergeClasses(styles.node, styles.nodeIntegrations)}
                icon={<PlugConnected24Regular id={"exchange-orchestration-canvas-plug-connected24-regular-1"}
                                              aria-hidden="true"/>}
            />

            <ExchangeWorkspaceCard/>

            <div
                id="orchestration-event-approved"
                className={mergeClasses(styles.eventChip, styles.eventApproved)}
                aria-hidden="true"
            >
                <CheckmarkCircle24Filled id={"exchange-orchestration-canvas-checkmark-circle24-filled-1"}/>
                Approved
            </div>
            <div
                id="orchestration-event-audit"
                className={mergeClasses(styles.eventChip, styles.eventAudit)}
                aria-hidden="true"
            >
                <History24Regular id={"exchange-orchestration-canvas-history24-regular-2"}/>
                Audit recorded
            </div>
        </div>
    );
}

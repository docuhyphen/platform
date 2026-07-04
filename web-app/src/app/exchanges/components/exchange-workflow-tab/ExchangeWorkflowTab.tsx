import {useEffect, useState} from "react";
import {MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {ExchangeClearanceStatusDto, ExchangeDetailedDto, WorkflowInstanceDetailDto} from "../../../models/models.tsx";
import {PendingWorkflowStep} from "../../../../services/types/dtos.ts";
import {fetchExchangeWorkflowClearanceStatus, fetchExchangeWorkflowInstances} from "../../../../services/exchangeApi.ts";
import {getWorkflowInstanceDetail} from "../../../../services/workflowService.ts";
import {getMyPendingDecisions} from "../../../../services/workflowApi.ts";
import {useExchangeWorkflowTabStyles} from "./ExchangeWorkflowTabStyles.tsx";
import ClearanceStatusCard from "./ClearanceStatusCard.tsx";
import WorkflowInstanceSection from "./WorkflowInstanceSection.tsx";
import WorkflowViewToggle, {WorkflowViewMode} from "./workflow-view-toggle/WorkflowViewToggle.tsx";

interface Props
{
    exchange: ExchangeDetailedDto;
}

const ExchangeWorkflowTab = ({exchange}: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [instances, setInstances] = useState<WorkflowInstanceDetailDto[]>([]);
    const [pendingSteps, setPendingSteps] = useState<PendingWorkflowStep[]>([]);
    const [clearanceStatus, setClearanceStatus] = useState<ExchangeClearanceStatusDto | null>(null);
    // Tab-level view preference (Decision 8, Decision 15): local state only, default Timeline.
    const [viewMode, setViewMode] = useState<WorkflowViewMode>("TIMELINE");

    const fetchData = async () =>
    {
        setLoading(true);
        setError(null);
        try
        {
            const [summaries, pending] = await Promise.all([
                fetchExchangeWorkflowInstances(exchange.id),
                getMyPendingDecisions(),
            ]);
            const details = await Promise.all(summaries.map(s => getWorkflowInstanceDetail(s.id)));
            setInstances(details);
            setPendingSteps(pending);

            // Clearance status is best-effort; do not fail the tab if it errors.
            fetchExchangeWorkflowClearanceStatus(exchange.id)
                .then(cs => setClearanceStatus(cs))
                .catch(() => setClearanceStatus(null));
        }
        catch (e: unknown)
        {
            const err = e as {errorMessage?: string; message?: string} | null;
            setError(err?.errorMessage || err?.message || "Failed to load workflow information.");
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        fetchData();
    }, [exchange.id]);

    if (loading)
    {
        return (
            <div id="exchange-workflow-tab-loading"
                 className={styles.centeredState}>
                <Spinner size="small"
                         label="Loading workflow..."
                         labelPosition="after" />
            </div>
        );
    }

    if (error)
    {
        return (
            <MessageBar id="exchange-workflow-tab-error"
                        intent="error">
                <MessageBarBody>{error}</MessageBarBody>
            </MessageBar>
        );
    }

    if (instances.length === 0)
    {
        return (
            <div id="exchange-workflow-tab-empty"
                 className={styles.emptyCard}>
                <Text>This exchange does not require any workflows.</Text>
            </div>
        );
    }

    return (
        <div id="exchange-workflow-tab"
             className={styles.root}>
            <div id="exchange-workflow-tab-header"
                 className={styles.header}>
                <Text className={styles.headerTitle}
                      weight="semibold">
                    Workflow
                </Text>
                <WorkflowViewToggle mode={viewMode}
                                    onChange={setViewMode} />
            </div>

            <div id="exchange-workflow-tab-content"
                 className={styles.content}>
                {clearanceStatus && <ClearanceStatusCard clearance={clearanceStatus} />}
                {instances.map(instance => (
                    <WorkflowInstanceSection key={instance.id}
                                             instance={instance}
                                             pendingSteps={pendingSteps}
                                             onDecisionMade={fetchData}
                                             viewMode={viewMode}
                                             onViewTimeline={() => setViewMode("TIMELINE")} />
                ))}
            </div>
        </div>
    );
};

export default ExchangeWorkflowTab;

import { useEffect, useState } from "react";
import { MessageBar, MessageBarBody, Spinner, Text } from "@fluentui/react-components";
import { ExchangeClearanceStatusDto, ExchangeDetailedDto, WorkflowInstanceDetailDto } from "../../../models/models.tsx";
import { PendingWorkflowStep } from "../../../../services/types/dtos.ts";
import { fetchExchangeWorkflowClearanceStatus, fetchExchangeWorkflowInstances } from "../../../../services/exchangeApi.ts";
import { getWorkflowInstanceDetail } from "../../../../services/workflowService.ts";
import { getMyPendingDecisions } from "../../../../services/workflowApi.ts";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";
import ClearanceStatusCard from "./ClearanceStatusCard.tsx";
import WorkflowSummaryCard from "./WorkflowSummaryCard.tsx";
import RejectionBanner from "./RejectionBanner.tsx";
import ActionRequiredCard from "./ActionRequiredCard.tsx";
import WorkflowTimeline from "./WorkflowTimeline.tsx";

interface SectionProps
{
    instance: WorkflowInstanceDetailDto;
    pendingSteps: PendingWorkflowStep[];
    onDecisionMade: () => void;
    showHeading: boolean;
}

const WorkflowInstanceSection = ({ instance, pendingSteps, onDecisionMade, showHeading }: SectionProps) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const pendingStep = pendingSteps.find(p => p.workflowInstanceId === instance.id);

    return (
        <div id={`workflow-instance-section-${instance.id}`}
             className={styles.instanceSection}>
            <RejectionBanner instance={instance} />
            <WorkflowSummaryCard instance={instance} />
            {pendingStep && (
                <ActionRequiredCard
                    instance={instance}
                    pendingStep={pendingStep}
                    onDecisionMade={onDecisionMade}
                />
            )}
            <WorkflowTimeline instance={instance} />
        </div>
    );
};

interface Props
{
    exchange: ExchangeDetailedDto;
}

const ExchangeWorkflowTab = ({ exchange }: Props) =>
{
    const styles = useExchangeWorkflowTabStyles();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [instances, setInstances] = useState<WorkflowInstanceDetailDto[]>([]);
    const [pendingSteps, setPendingSteps] = useState<PendingWorkflowStep[]>([]);
    const [clearanceStatus, setClearanceStatus] = useState<ExchangeClearanceStatusDto | null>(null);

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
        catch (e: any)
        {
            setError(e?.errorMessage || e?.message || "Failed to load workflow information.");
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
            {clearanceStatus && <ClearanceStatusCard clearance={clearanceStatus} />}
            {instances.map(instance => (
                <WorkflowInstanceSection
                    key={instance.id}
                    instance={instance}
                    pendingSteps={pendingSteps}
                    onDecisionMade={fetchData}
                    showHeading={instances.length > 1}
                />
            ))}
        </div>
    );
};

export default ExchangeWorkflowTab;

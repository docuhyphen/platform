import { useEffect, useState } from "react";
import { MessageBar, MessageBarBody, Spinner, Text } from "@fluentui/react-components";
import { ExchangeDetailedDto, WorkflowInstanceDetailDto } from "../../../models/models.tsx";
import { PendingWorkflowStep } from "../../../../services/types/dtos.ts";
import { fetchExchangeWorkflowInstances } from "../../../../services/exchangeApi.ts";
import { getWorkflowInstanceDetail } from "../../../../services/workflowService.ts";
import { getMyPendingDecisions } from "../../../../services/workflowApi.ts";
import { useExchangeWorkflowTabStyles } from "./ExchangeWorkflowTabStyles.tsx";
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
        <div className={styles.instanceSection}>
            {/*{showHeading && (*/}
            {/*    <Text weight="semibold" size={400} className={styles.sectionHeading} block>*/}
            {/*        {instance.definitionName ?? "Workflow"}*/}
            {/*    </Text>*/}
            {/*)}*/}
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
            <div className={styles.centeredState}>
                <Spinner size="small" label="Loading workflow..." labelPosition="after" />
            </div>
        );
    }

    if (error)
    {
        return (
            <MessageBar intent="error">
                <MessageBarBody>{error}</MessageBarBody>
            </MessageBar>
        );
    }

    if (instances.length === 0)
    {
        return (
            <div className={styles.emptyCard}>
                <Text>This exchange does not require workflow approval.</Text>
            </div>
        );
    }

    return (
        <div className={styles.root}>
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

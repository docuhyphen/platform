import {useEffect} from 'react';
import {ExchangeWorkspaceState} from './useExchangeWorkspaceState.ts';

export interface ExchangeDetailsTabCapabilities
{
    canViewBusinessFields: boolean;
    canViewWorkflows: boolean;
    canViewExchangeAudit: boolean;
}

interface InformationRequestsTabAvailability
{
    loading: boolean;
    visible: boolean;
}

export const useExchangeDetailsTabAvailability = (
    state: ExchangeWorkspaceState,
    capabilities: ExchangeDetailsTabCapabilities,
    informationRequests: InformationRequestsTabAvailability,
) =>
{
    const {detailsActiveTab, setDetailsActiveTab} = state;
    const {canViewBusinessFields, canViewWorkflows, canViewExchangeAudit} = capabilities;

    useEffect(() =>
    {
        if (
            (detailsActiveTab === "details" && !canViewBusinessFields)
            || (detailsActiveTab === "workflow" && !canViewWorkflows)
            || (detailsActiveTab === "audit" && !canViewExchangeAudit)
            || (detailsActiveTab === "information-requests" && !informationRequests.loading && !informationRequests.visible)
        )
        {
            setDetailsActiveTab("documents");
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [canViewBusinessFields, canViewExchangeAudit, canViewWorkflows, detailsActiveTab, informationRequests.loading, informationRequests.visible]);
};

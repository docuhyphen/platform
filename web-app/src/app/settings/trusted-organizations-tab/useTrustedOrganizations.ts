import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useNotifications} from "../../../context/NotificationContext.tsx";
import {
    decideOrganizationTrust,
    fetchTrustPolicies,
    fetchTrustRelationships,
    OrganizationTrustPolicies,
    OrganizationTrustPolicyUpdate,
    OrganizationTrustRelationship,
    requestOrganizationTrust,
    resumeOrganizationTrust,
    suspendOrganizationTrust,
    terminateOrganizationTrust,
    updateOrganizationTrustPolicy,
    withdrawOrganizationTrust,
} from "../../../services/organizationTrust.ts";

export type TrustRelationshipAction = "ACCEPT" | "REJECT" | "WITHDRAW" | "SUSPEND" | "RESUME" | "END";

export const useTrustedOrganizations = () =>
{
    const {currentSession} = useAuth();
    const activeOrganizationId = currentSession?.activeOrganizationId ?? null;
    const {notifications} = useNotifications();
    const [relationships, setRelationships] = useState<OrganizationTrustRelationship[]>([]);
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [policies, setPolicies] = useState<OrganizationTrustPolicies | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Stale-response protection: relationship and policy responses are only applied when they are
    // the newest request for the still-current active organization. This prevents a response that
    // started under a previous active organization or an older selection from repopulating state.
    const activeOrganizationRef = useRef(activeOrganizationId);
    activeOrganizationRef.current = activeOrganizationId;
    const relationshipRequestRef = useRef(0);
    const policyRequestRef = useRef(0);

    const refresh = useCallback(async () =>
    {
        const requestId = ++relationshipRequestRef.current;
        const requestOrganizationId = activeOrganizationId;
        const isCurrent = () =>
            requestId === relationshipRequestRef.current && requestOrganizationId === activeOrganizationRef.current;
        setLoading(true);
        setError(null);
        try
        {
            const next = await fetchTrustRelationships();
            if (!isCurrent()) return;
            setRelationships(next);
            setSelectedId(current => current && next.some(item => item.id === current) ? current : next[0]?.id ?? null);
        }
        catch (requestError: unknown)
        {
            if (!isCurrent()) return;
            setError(requestError instanceof Error ? requestError.message : "Unable to load Trusted Organizations");
        }
        finally
        {
            if (isCurrent()) setLoading(false);
        }
    }, [activeOrganizationId]);

    const selected = useMemo(
        () => relationships.find(relationship => relationship.id === selectedId) ?? null,
        [relationships, selectedId],
    );

    useEffect(() =>
    {
        setRelationships([]);
        setPolicies(null);
        setSelectedId(null);
        void refresh();
    }, [activeOrganizationId, refresh]);

    useEffect(() =>
    {
        if (notifications.some(notification => notification.data?.source === "organization-trust"))
        {
            void refresh();
        }
    }, [notifications, refresh]);

    useEffect(() =>
    {
        setPolicies(null);
        if (!selected)
        {
            return;
        }
        const requestId = ++policyRequestRef.current;
        const requestOrganizationId = activeOrganizationId;
        const relationshipId = selected.id;
        const isCurrent = () =>
            requestId === policyRequestRef.current && requestOrganizationId === activeOrganizationRef.current;
        void fetchTrustPolicies(relationshipId)
            .then(result =>
            {
                if (isCurrent()) setPolicies(result);
            })
            .catch((requestError: unknown) =>
            {
                if (isCurrent())
                {
                    setError(requestError instanceof Error ? requestError.message : "Unable to load trust policies");
                }
            });
    }, [selected, activeOrganizationId]);

    const run = async (operation: () => Promise<unknown>) =>
    {
        setBusy(true);
        setError(null);
        try
        {
            await operation();
            await refresh();
        }
        catch (requestError: unknown)
        {
            setError(requestError instanceof Error ? requestError.message : "Trusted Organization action failed");
            throw requestError;
        }
        finally
        {
            setBusy(false);
        }
    };

    const act = async (action: TrustRelationshipAction, relationship: OrganizationTrustRelationship, reason?: string) =>
        run(() =>
        {
            switch (action)
            {
                case "ACCEPT":
                case "REJECT":
                    return decideOrganizationTrust(relationship, action, reason);
                case "WITHDRAW":
                    return withdrawOrganizationTrust(relationship, reason);
                case "SUSPEND":
                    return suspendOrganizationTrust(relationship.id, reason ?? "");
                case "RESUME":
                    if (!relationship.currentOrganizationSuspensionId)
                    {
                        return Promise.reject(new Error("This relationship has no active suspension to resume"));
                    }
                    return resumeOrganizationTrust(relationship.id, relationship.currentOrganizationSuspensionId);
                case "END":
                    return terminateOrganizationTrust(relationship, reason);
            }
        });

    const requestTrust = (organizationId: string, message?: string) => run(
        () => requestOrganizationTrust(organizationId, message),
    );
    const savePolicy = (policyId: string, update: OrganizationTrustPolicyUpdate) => selected
        ? run(() => updateOrganizationTrustPolicy(selected.id, policyId, update))
        : Promise.resolve();

    return {
        relationships, selected, policies, loading, busy, error, setError, setSelectedId,
        refresh, act, requestTrust, savePolicy,
    };
};

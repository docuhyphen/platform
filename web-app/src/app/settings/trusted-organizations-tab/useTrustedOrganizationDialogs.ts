import {useEffect, useState} from "react";
import {OrganizationTrustRelationship} from "../../../services/organizationTrust.ts";
import {TrustRelationshipAction, useTrustedOrganizations} from "./useTrustedOrganizations.ts";

interface PendingTrustAction
{
    action: TrustRelationshipAction;
    relationship: OrganizationTrustRelationship;
}

export const useTrustedOrganizationDialogs = (
    activeOrganizationId: string | undefined,
    trust: ReturnType<typeof useTrustedOrganizations>,
) =>
{
    const [requestOpen, setRequestOpen] = useState(false);
    const [pending, setPending] = useState<PendingTrustAction | null>(null);

    useEffect(() =>
    {
        setRequestOpen(false);
        setPending(null);
    }, [activeOrganizationId]);

    const openAction = (action: TrustRelationshipAction, relationship: OrganizationTrustRelationship) =>
        setPending({action, relationship});

    const confirmAction = async (reason?: string) =>
    {
        if (!pending) return;
        const current = trust.relationships.find(item => item.id === pending.relationship.id);
        if (!current || current.version !== pending.relationship.version)
        {
            setPending(null);
            trust.setError("This relationship changed before the action was confirmed. Review the latest state and try again.");
            return;
        }
        try
        {
            await trust.act(pending.action, pending.relationship, reason);
            setPending(null);
        }
        catch
        {
            return;
        }
    };

    const requestTrust = async (organizationId: string, message?: string) =>
    {
        try
        {
            await trust.requestTrust(organizationId, message);
            setRequestOpen(false);
        }
        catch
        {
            return;
        }
    };

    return {
        requestOpen,
        setRequestOpen,
        pending,
        setPending,
        openAction,
        confirmAction,
        requestTrust,
    };
};

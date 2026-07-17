import {useCallback, useEffect, useRef, useState} from "react";
import {OrganizationBasicDto, Capability} from "../../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../../services/organizationApi.ts";
import {
    ExternalIdentityResolution,
    fetchPublishedExchangeGroups,
    fetchTrustRelationships,
    OrganizationTrustRelationship,
    PublishedExchangeGroup,
    resolveExternalIdentity,
} from "../../../../../services/organizationTrust.ts";
import {useAuth} from "../../../../../context/AuthContext.tsx";
import {TrustedRecipientMethod} from "./TrustedRecipientMethodSelector.tsx";

interface TrustedOrganizationRecipientStateProps
{
    setRecipientOrg: (organization?: OrganizationBasicDto) => void;
    setRecipientOrgUser: (user: undefined) => void;
    setRecipientOrgGroup: (group?: OrganizationGroupBasicDto) => void;
    setRecipientResolution: (resolution?: ExternalIdentityResolution) => void;
}

export const useTrustedOrganizationRecipients = (props: TrustedOrganizationRecipientStateProps) =>
{
    const {currentSession, hasCapability} = useAuth();
    const {
        setRecipientOrg,
        setRecipientOrgGroup,
        setRecipientOrgUser,
        setRecipientResolution,
    } = props;
    const canResolvePerson = hasCapability(Capability.EXTERNAL_IDENTITY_RESOLVE);
    const canDiscoverGroups = hasCapability(Capability.EXTERNAL_GROUP_DISCOVER);
    const [relationships, setRelationships] = useState<OrganizationTrustRelationship[]>([]);
    const [groups, setGroups] = useState<PublishedExchangeGroup[]>([]);
    const [selectedRelationshipId, setSelectedRelationshipId] = useState<string>();
    const [method, setMethod] = useState<TrustedRecipientMethod>(canResolvePerson ? "PERSON" : "GROUP");
    const [email, setEmail] = useState("");
    const [resolution, setResolution] = useState<ExternalIdentityResolution>();
    const [resolutionExpired, setResolutionExpired] = useState(false);
    const [loadingRelationships, setLoadingRelationships] = useState(false);
    const [loadingGroups, setLoadingGroups] = useState(false);
    const [resolvingPerson, setResolvingPerson] = useState(false);
    const [error, setError] = useState<string>();
    const groupRequestId = useRef(0);
    const resolutionRequestId = useRef(0);
    const selectedRelationship = relationships.find((item) => item.id === selectedRelationshipId);

    const clearDestination = useCallback(() =>
    {
        groupRequestId.current += 1;
        resolutionRequestId.current += 1;
        setGroups([]);
        setEmail("");
        setResolution(undefined);
        setResolutionExpired(false);
        setError(undefined);
        setLoadingGroups(false);
        setResolvingPerson(false);
        setRecipientOrgUser(undefined);
        setRecipientOrgGroup(undefined);
        setRecipientResolution(undefined);
    }, [setRecipientOrgGroup, setRecipientOrgUser, setRecipientResolution]);

    useEffect(() =>
    {
        clearDestination();
        setRelationships([]);
        setSelectedRelationshipId(undefined);
        setRecipientOrg(undefined);
        setMethod(canResolvePerson ? "PERSON" : "GROUP");
        if (!currentSession?.activeOrganizationId || (!canResolvePerson && !canDiscoverGroups))
        {
            return;
        }

        let active = true;
        setLoadingRelationships(true);
        void fetchTrustRelationships()
            .then((items) => active && setRelationships(items.filter((relationship) =>
                relationship.status === "ACTIVE" &&
                !relationship.effectivelySuspended &&
                relationship.partnerOrganizationActive &&
                relationship.partnerOrganizationVerified)))
            .catch((reason: unknown) => active && setError(
                reason instanceof Error ? reason.message : "Unable to load Trusted Organizations",
            ))
            .finally(() => active && setLoadingRelationships(false));
        return () =>
        {
            active = false;
        };
    }, [
        canDiscoverGroups,
        canResolvePerson,
        clearDestination,
        currentSession?.activeOrganizationId,
        setRecipientOrg,
    ]);

    useEffect(() =>
    {
        if (!resolution)
        {
            return;
        }
        const remaining = resolution.expiresAt - Date.now();
        const expire = () =>
        {
            setResolutionExpired(true);
            setRecipientResolution(undefined);
        };
        if (remaining <= 0)
        {
            expire();
            return;
        }
        const timer = window.setTimeout(expire, remaining);
        return () => window.clearTimeout(timer);
    }, [resolution, setRecipientResolution]);

    const loadGroups = useCallback((organizationId: string) =>
    {
        const requestId = ++groupRequestId.current;
        setLoadingGroups(true);
        void fetchPublishedExchangeGroups(organizationId)
            .then((items) => requestId === groupRequestId.current && setGroups(items))
            .catch((reason: unknown) => requestId === groupRequestId.current && setError(
                reason instanceof Error ? reason.message : "Published groups are unavailable",
            ))
            .finally(() => requestId === groupRequestId.current && setLoadingGroups(false));
    }, []);

    const selectRelationship = useCallback((relationshipId?: string) =>
    {
        const relationship = relationships.find((item) => item.id === relationshipId);
        clearDestination();
        setSelectedRelationshipId(relationship?.id);
        setRecipientOrg(relationship ? {
            id: relationship.partnerOrganizationId,
            name: relationship.partnerOrganizationName,
            registrationNumber: "",
            verificationComplete: relationship.partnerOrganizationVerified,
            isActive: relationship.partnerOrganizationActive,
        } : undefined);
        if (relationship && method === "GROUP")
        {
            loadGroups(relationship.partnerOrganizationId);
        }
    }, [clearDestination, loadGroups, method, relationships, setRecipientOrg]);

    const selectMethod = useCallback((nextMethod: TrustedRecipientMethod) =>
    {
        clearDestination();
        setMethod(nextMethod);
        if (nextMethod === "GROUP" && selectedRelationship?.partnerOrganizationId)
        {
            loadGroups(selectedRelationship.partnerOrganizationId);
        }
    }, [clearDestination, loadGroups, selectedRelationship?.partnerOrganizationId]);

    const changeEmail = useCallback((value: string) =>
    {
        resolutionRequestId.current += 1;
        setEmail(value);
        setResolution(undefined);
        setResolutionExpired(false);
        setError(undefined);
        setRecipientResolution(undefined);
    }, [setRecipientResolution]);

    const resolvePerson = useCallback(() =>
    {
        if (!selectedRelationship?.partnerOrganizationId)
        {
            return;
        }
        const requestId = ++resolutionRequestId.current;
        setResolvingPerson(true);
        setResolution(undefined);
        setResolutionExpired(false);
        setError(undefined);
        void resolveExternalIdentity(selectedRelationship.partnerOrganizationId, email)
            .then((result) =>
            {
                if (requestId === resolutionRequestId.current)
                {
                    setResolution(result);
                    const expired = result.expiresAt <= Date.now();
                    setResolutionExpired(expired);
                    setRecipientResolution(expired ? undefined : result);
                }
            })
            .catch((reason: unknown) => requestId === resolutionRequestId.current && setError(
                reason instanceof Error ? reason.message : "Trusted member could not be verified",
            ))
            .finally(() => requestId === resolutionRequestId.current && setResolvingPerson(false));
    }, [email, selectedRelationship?.partnerOrganizationId, setRecipientResolution]);

    const selectGroup = useCallback((groupId?: string) =>
    {
        const group = groups.find((item) => item.id === groupId);
        setRecipientOrgGroup(group ? {...group, members: []} : undefined);
    }, [groups, setRecipientOrgGroup]);

    return {
        canDiscoverGroups,
        canResolvePerson,
        changeEmail,
        email,
        error,
        groups,
        loadingGroups,
        loadingRelationships,
        method,
        relationships,
        resolution,
        resolutionExpired,
        resolvePerson,
        resolvingPerson,
        selectedRelationshipId,
        selectGroup,
        selectMethod,
        selectRelationship,
    };
};

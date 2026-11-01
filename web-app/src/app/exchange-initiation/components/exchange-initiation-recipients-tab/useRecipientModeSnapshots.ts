import {useEffect, useRef} from 'react';
import type {FormEvent} from 'react';
import {AppUserPublicDto, OrganizationBasicDto} from "../../../models/models.tsx";
import {OrganizationGroupBasicDto} from "../../../../services/organizationApi";
import {ExchangeNewMainRecipient} from "./new-recipient/NewRecipient";
import {ExchangeInitiationRecipientMode} from "./exchangeInitiationRecipientMode.ts";

interface RecipientModeSnapshot
{
    recipientOrg?: OrganizationBasicDto;
    recipientOrgUser?: AppUserPublicDto;
    recipientOrgGroup?: OrganizationGroupBasicDto;
    internalParticipants?: AppUserPublicDto[];
    newRecipient?: ExchangeNewMainRecipient;
}

export interface UseRecipientModeSnapshotsParams
{
    recipientMode: ExchangeInitiationRecipientMode;
    setRecipientMode: (mode: ExchangeInitiationRecipientMode) => void;
    recipientOrg: OrganizationBasicDto | undefined;
    setRecipientOrg: (org: OrganizationBasicDto | undefined) => void;
    recipientOrgUser: AppUserPublicDto | undefined;
    setRecipientOrgUser: (user: AppUserPublicDto | undefined) => void;
    recipientOrgGroup: OrganizationGroupBasicDto | undefined;
    setRecipientOrgGroup: (group: OrganizationGroupBasicDto | undefined) => void;
    internalParticipants: AppUserPublicDto[];
    setInternalParticipants: (appUser: AppUserPublicDto[]) => void;
    newRecipient: ExchangeNewMainRecipient | undefined;
    setNewRecipient: (recipient: ExchangeNewMainRecipient | undefined) => void;
}

export function useRecipientModeSnapshots(params: UseRecipientModeSnapshotsParams)
{
    const snapshots = useRef<Record<ExchangeInitiationRecipientMode, RecipientModeSnapshot>>({
        [ExchangeInitiationRecipientMode.PEOPLE]: {},
        [ExchangeInitiationRecipientMode.MY_GROUPS]: {},
        [ExchangeInitiationRecipientMode.MY_ORG]: {},
        [ExchangeInitiationRecipientMode.TRUSTED_ORG]: {},
        [ExchangeInitiationRecipientMode.EMAIL]: {},
    });

    const captureCurrent = (): RecipientModeSnapshot => ({
        recipientOrg: params.recipientOrg,
        recipientOrgUser: params.recipientOrgUser,
        recipientOrgGroup: params.recipientOrgGroup,
        internalParticipants: params.internalParticipants,
        newRecipient: params.newRecipient,
    });

    useEffect(() =>
    {
        snapshots.current[params.recipientMode] = captureCurrent();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [
        params.recipientMode,
        params.recipientOrg,
        params.recipientOrgUser,
        params.recipientOrgGroup,
        params.internalParticipants,
        params.newRecipient,
    ]);

    const onRecipientModeChange = (
        _: FormEvent<HTMLDivElement>,
        data: { value: ExchangeInitiationRecipientMode }) =>
    {
        const nextMode = data.value;
        if (nextMode === params.recipientMode)
        {
            return;
        }

        snapshots.current[params.recipientMode] = captureCurrent();
        params.setRecipientMode(nextMode);

        const snapshot = snapshots.current[nextMode];
        params.setRecipientOrg(snapshot.recipientOrg);
        params.setRecipientOrgUser(snapshot.recipientOrgUser);
        params.setRecipientOrgGroup(snapshot.recipientOrgGroup);
        params.setInternalParticipants(snapshot.internalParticipants ?? []);
        if (snapshot.newRecipient)
        {
            params.setNewRecipient(snapshot.newRecipient);
        }
    };

    return {onRecipientModeChange};
}

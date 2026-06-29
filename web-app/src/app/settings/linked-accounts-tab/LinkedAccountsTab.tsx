import React, {useEffect, useState} from 'react';
import {
    MessageBar,
    MessageBarBody,
    Spinner,
} from "@fluentui/react-components";
import {useLinkedAccountsTabStyles} from './LinkedAccountsTabStyles';
import {IdentityProviderLinkDto, ResponseError} from "../../models/models.tsx";
import {getIdentityProviders, initiateLinkProvider, unlinkProvider} from "../../../services/authApi.ts";
import LinkedProviderCard from "./linked-provider-card/LinkedProviderCard.tsx";
import LinkedAccountsSummary from "./linked-accounts-summary/LinkedAccountsSummary.tsx";
import {providerDefinitions} from "./providerDefinitions.ts";

const LinkedAccountsTab: React.FC = () =>
{
    const styles = useLinkedAccountsTabStyles();
    const [providers, setProviders] = useState<IdentityProviderLinkDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | undefined>();
    const [actionLoading, setActionLoading] = useState<string | null>(null);

    const fetchProviders = async () =>
    {
        setLoading(true);
        try
        {
            const data = await getIdentityProviders();
            setProviders(data);
        }
        catch (e)
        {
            setError((e as ResponseError)?.errorMessage || "Failed to load linked accounts.");
        }
        finally
        {
            setLoading(false);
        }
    };

    useEffect(() =>
    {
        fetchProviders();
    }, []);

    const onLinkProvider = async (provider: string) =>
    {
        setActionLoading(provider);
        try
        {
            const response = await initiateLinkProvider(provider);
            if (response.redirectUrl)
            {
                window.location.href = response.redirectUrl;
            }
        }
        catch (e)
        {
            setError((e as ResponseError)?.errorMessage || "Failed to initiate linking.");
        }
        finally
        {
            setActionLoading(null);
        }
    };

    const onUnlinkProvider = async (provider: string) =>
    {
        setActionLoading(provider);
        try
        {
            await unlinkProvider(provider);
            await fetchProviders();
        }
        catch (e)
        {
            setError((e as ResponseError)?.errorMessage || "Failed to unlink provider.");
        }
        finally
        {
            setActionLoading(null);
        }
    };

    const isLinked = (provider: string) => providers.some(p => p.provider === provider);
    const connectedCount = providers.length;
    const canUnlink = connectedCount > 1;

    if (loading)
    {
        return <Spinner
            id={"linked-accounts-loading"}
            label="Loading linked accounts..."
        />;
    }

    return (
        <div
            id={"linked-accounts-tab-container"}
            className={styles.container}>
            <LinkedAccountsSummary connectedCount={connectedCount}/>

            {error && (
                <MessageBar
                    id={"linked-accounts-error"}
                    intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            <div
                id={"linked-accounts-provider-list"}
                className={styles.providerList}>
                {providerDefinitions.map((providerCard) =>
                {
                    const linked = isLinked(providerCard.provider);

                    return <LinkedProviderCard
                        id={`linked-provider-card-${providerCard.idPrefix}`}
                        key={providerCard.provider}
                        title={providerCard.title}
                        description={providerCard.description}
                        providerMark={providerCard.providerMark}
                        linked={linked}
                        isActionLoading={actionLoading === providerCard.provider}
                        canUnlink={canUnlink}
                        onLink={() => onLinkProvider(providerCard.provider)}
                        onUnlink={() => onUnlinkProvider(providerCard.provider)}
                    />;
                })}
            </div>
        </div>
    );
};

export default LinkedAccountsTab;

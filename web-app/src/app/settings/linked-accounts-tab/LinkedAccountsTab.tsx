import React, {useEffect, useState} from 'react';
import {
    Button,
    MessageBar,
    MessageBarBody,
    Spinner,
    Subtitle2,
    Text,
    Card,
    CardHeader,
    Badge,
} from "@fluentui/react-components";
import {IdentityProviderLinkDto, ResponseError} from "../../models/models.tsx";
import {getIdentityProviders, initiateLinkProvider, unlinkProvider} from "../../../services/authApi.ts";

const LinkedAccountsTab: React.FC = () =>
{
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
    const canUnlink = providers.length > 1;

    if (loading)
    {
        return <Spinner label="Loading linked accounts..." />;
    }

    return (
        <div style={{display: 'flex', flexDirection: 'column', gap: '16px', maxWidth: '500px'}}>

            <Text size={200}>
                Manage the identity providers linked to your account.
                You can sign in using any linked provider.
            </Text>

            {error && (
                <MessageBar intent="error">
                    <MessageBarBody>{error}</MessageBarBody>
                </MessageBar>
            )}

            {/* Internal (Email + Password) */}
            <Card>
                <CardHeader
                    header={<Text weight="semibold">Email & Password</Text>}
                    description={isLinked('INTERNAL') ? <Badge appearance="filled" color="success">Linked</Badge> : <Badge appearance="outline">Not linked</Badge>}
                    action={
                        isLinked('INTERNAL')
                            ? <Button appearance="secondary"
                                      disabled={!canUnlink || actionLoading === 'INTERNAL'}
                                      shape={"circular"}
                                      onClick={() => onUnlinkProvider('INTERNAL')}>
                                {actionLoading === 'INTERNAL' ? <Spinner size="tiny"/> : "Unlink"}
                            </Button>
                            : null
                    }
                />
            </Card>

            {/* Microsoft */}
            <Card>
                <CardHeader
                    header={<Text weight="semibold">Microsoft</Text>}
                    description={isLinked('MICROSOFT') ? <Badge appearance="filled" color="success">Linked</Badge> : <Badge appearance="outline">Not linked</Badge>}
                    action={
                        isLinked('MICROSOFT')
                            ? <Button appearance="secondary"
                                      disabled={!canUnlink || actionLoading === 'MICROSOFT'}
                                      shape={"circular"}
                                      onClick={() => onUnlinkProvider('MICROSOFT')}>
                                {actionLoading === 'MICROSOFT' ? <Spinner size="tiny"/> : "Unlink"}
                            </Button>
                            : <Button appearance="secondary"
                                      disabled={actionLoading === 'MICROSOFT'}
                                      shape={"circular"}
                                      onClick={() => onLinkProvider('MICROSOFT')}>
                                {actionLoading === 'MICROSOFT' ? <Spinner size="tiny"/> : "Link"}
                            </Button>
                    }
                />
            </Card>

            {/* Google */}
            <Card>
                <CardHeader
                    header={<Text weight="semibold">Google</Text>}
                    description={isLinked('GOOGLE') ? <Badge appearance="filled" color="success">Linked</Badge> : <Badge appearance="outline">Not linked</Badge>}
                    action={
                        isLinked('GOOGLE')
                            ? <Button appearance="secondary"
                                      disabled={!canUnlink || actionLoading === 'GOOGLE'}
                                      shape={"circular"}
                                      onClick={() => onUnlinkProvider('GOOGLE')}>
                                {actionLoading === 'GOOGLE' ? <Spinner size="tiny"/> : "Unlink"}
                            </Button>
                            : <Button appearance="secondary" disabled={actionLoading === 'GOOGLE'}
                                      shape={"circular"}
                                      onClick={() => onLinkProvider('GOOGLE')}>
                                {actionLoading === 'GOOGLE' ? <Spinner size="tiny"/> : "Link"}
                            </Button>
                    }
                />
            </Card>

            {!canUnlink && (
                <Text size={200} italic>
                    You must have at least one linked provider. Unlink is disabled when only one remains.
                </Text>
            )}
        </div>
    );
};

export default LinkedAccountsTab;


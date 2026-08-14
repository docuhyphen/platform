import React, {useEffect, useState} from 'react';
import {MessageBar, MessageBarBody, Spinner} from "@fluentui/react-components";
import {useLinkedAccountsTabStyles} from './LinkedAccountsTabStyles';
import {IdentityProviderLinkDto, ResponseError} from "../../models/models.tsx";
import {getIdentityProviders, initiateLinkProvider} from "../../../services/authApi.ts";
import LinkedAccountsSummary from "./linked-accounts-summary/LinkedAccountsSummary.tsx";
import LinkedProviderList from "./linked-provider-list/LinkedProviderList.tsx";
import SetupPasswordDialog from "./setup-password-dialog/SetupPasswordDialog.tsx";
import {useSetupPasswordLinkFlow} from "./setup-password-navigation/useSetupPasswordLinkFlow.ts";
import UnlinkProviderDialog from "./unlink-provider-dialog/UnlinkProviderDialog.tsx";
import {useUnlinkProviderFlow} from "./useUnlinkProviderFlow.ts";

const LinkedAccountsTab: React.FC = () =>
{
    const styles = useLinkedAccountsTabStyles();
    const [providers, setProviders] = useState<IdentityProviderLinkDto[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | undefined>();
    const [actionLoading, setActionLoading] = useState<string | null>(null);
    const {passwordDialogOpen, beginPasswordSetup, closePasswordDialog} = useSetupPasswordLinkFlow();

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
        if (provider === "INTERNAL")
        {
            setActionLoading(provider);
            try
            {
                await beginPasswordSetup();
            }
            catch (e)
            {
                setError((e as ResponseError)?.errorMessage || "Re-authentication was not completed.");
            }
            finally
            {
                setActionLoading(null);
            }
            return;
        }

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

    const unlinkFlow = useUnlinkProviderFlow({
        refreshProviders: fetchProviders,
        setError,
        setActionLoading,
    });

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

            <LinkedProviderList
                linkedProviders={providers.map(provider => provider.provider)}
                actionLoading={actionLoading}
                canUnlink={canUnlink}
                onLink={onLinkProvider}
                onUnlink={unlinkFlow.requestUnlink}
            />
            <SetupPasswordDialog
                open={passwordDialogOpen}
                onClose={closePasswordDialog}
            />
            <UnlinkProviderDialog
                provider={unlinkFlow.pendingProvider}
                unlinking={unlinkFlow.unlinking}
                onConfirm={unlinkFlow.confirmUnlink}
                onCancel={unlinkFlow.cancelUnlink}
            />
        </div>
    );
};

export default LinkedAccountsTab;

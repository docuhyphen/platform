import React, {useEffect, useState} from "react";
import {
    Button,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    SelectTabData,
    SelectTabEvent,
    Spinner,
    Tab,
    TabList,
    TabValue,
    Text
} from "@fluentui/react-components";
import {useOrganizationParingTabStyles} from "./OrganizationParingTabStyles.tsx";
import {DismissRegular, LinkAddRegular} from "@fluentui/react-icons";
import {OrganizationParingRequestsTabIcon, ParedOrganizationsTabIcon} from "../../components/IconBundles.tsx";
import ParedOrganizationsTab from "./pared-organizations-tab/ParedOrganizationsTab.tsx";
import OrganizationParingRequestsTab from "./paring-requests/ParingRequestsTab.tsx";
import {fetchOrganizationLinks} from "../../../services/organizationSharingSession.ts";
import {useAuth} from "../../../context/AuthContext.tsx";
import {LinkStatus, OrganizationSharingSessionLinkBasicDto, ResponseError} from "../../models/models.tsx";
import ParingRequestDialog from "./paring-request-dialog/ParingRequestDialog.tsx";
import {useNotifications} from "../../../context/NotificationContext.tsx";

const OrganizationPairingTab = () =>
{
    const tabIds = {
        pairedOrganizations: "PairedOrganizationsTab",
        paringRequests: "ParingRequestsTab"
    }

    const {token} = useAuth()
    const styles = useOrganizationParingTabStyles()
    const [fetchingOrgPairs, setFetchingOrgPairs] = useState(false);
    const [tabErrorMessage, setTabErrorMessage] = useState<string | null>(null);
    const [selectedTab, setSelectedTab] = useState<TabValue>(tabIds.paringRequests);
    const [pairedOrgs, setPairedOrgs] = useState<OrganizationSharingSessionLinkBasicDto[]>([]);
    const [orgPairRequests, setOrgPairRequests] = useState<OrganizationSharingSessionLinkBasicDto[]>([]);
    const [isParingRequestDialogOpen, setIsParingRequestDialogOpen] = useState<boolean>(false);

    const fetchOrgPairs = async () =>
    {
        if (fetchingOrgPairs)
        {
            return
        }

        setPairedOrgs([])
        setOrgPairRequests([])
        setFetchingOrgPairs(true)

        try
        {
            const pairs = (await fetchOrganizationLinks()) as OrganizationSharingSessionLinkBasicDto[]

            if (pairs && pairs.length)
            {
                const pared = pairs.filter(
                    (l: OrganizationSharingSessionLinkBasicDto) => l.status === LinkStatus.ACCEPTED
                );

                const pairRequests = pairs.filter(
                    (l: OrganizationSharingSessionLinkBasicDto) => (l.status === LinkStatus.PENDING || l.status === LinkStatus.REJECTED)
                )

                setPairedOrgs(pared)
                setOrgPairRequests(pairRequests)
            }
        }
        catch (error: ResponseError | any)
        {
            const errorMessage = ((error as ResponseError)?.errorMessage) || "An unknown error occurred attempting to fetch pairs";
            setTabErrorMessage(errorMessage)
        }
        finally
        {
            setFetchingOrgPairs(false)
        }
    }

    useEffect(() =>
    {
        fetchOrgPairs()
    }, [token]);

    // Refresh on realtime org-pair notifications so the other side sees changes without reload.
    const {notifications} = useNotifications();
    useEffect(() =>
    {
        const orgPairNotif = notifications.find(n => n.data?.source === "org-pair");
        if (orgPairNotif)
        {
            fetchOrgPairs();
        }
    }, [notifications.length]);

    const renderTabError = () => (
        tabErrorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    <Text size={200}> {tabErrorMessage} </Text>
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            onClick={() => setTabErrorMessage(null)}
                            appearance="transparent"
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const onTabSelect = (event: SelectTabEvent, data: SelectTabData) =>
    {
        fetchOrgPairs().then(() =>
        {
            setSelectedTab(data.value);
        })
    };

    const onParingRequestDialogDismiss = () =>
    {
        setIsParingRequestDialogOpen(false)
    }

    const onParingRequestSent = (pair: OrganizationSharingSessionLinkBasicDto) =>
    {
        fetchOrgPairs().then(() =>
        {
            setIsParingRequestDialogOpen(false)
            setSelectedTab(tabIds.paringRequests)
        })
    }

    const onParingRequestDeleted = (pair: OrganizationSharingSessionLinkBasicDto) =>
    {
        fetchOrgPairs().then(() =>
        {
            setSelectedTab(tabIds.paringRequests)
        })
    }

    const onParingRequestAccepted = (pair: OrganizationSharingSessionLinkBasicDto) =>
    {
        fetchOrgPairs().then(() =>
        {
            setSelectedTab(tabIds.pairedOrganizations)
        })
    }

    const onParingRequestRejected = (pair: OrganizationSharingSessionLinkBasicDto) =>
    {
        fetchOrgPairs().then(() =>
        {
            setSelectedTab(tabIds.paringRequests)
        })
    }


    return <>
        {fetchingOrgPairs &&
            <Spinner label="Loading"
                     size={"small"}/>
        }

        {tabErrorMessage && renderTabError()}

        {!fetchingOrgPairs && <>

            <section className={styles.container}>
                <div className={styles.header}>
                    <Button
                        icon={<LinkAddRegular/>}
                        appearance="primary"
                        shape="circular"
                        onClick={() => setIsParingRequestDialogOpen(true)}>
                        Find & Pair
                    </Button>
                </div>

                <div className={styles.tabListContainer}>
                    <TabList selectedValue={selectedTab}
                             onTabSelect={onTabSelect}
                             size="medium"
                             vertical>
                        <Tab id={tabIds.paringRequests}
                             icon={<OrganizationParingRequestsTabIcon/>}
                             value={tabIds.paringRequests}>
                            Paring Requests
                        </Tab>
                        <Tab id={tabIds.pairedOrganizations}
                             icon={<ParedOrganizationsTabIcon/>}
                             value={tabIds.pairedOrganizations}>
                            Pared
                        </Tab>
                    </TabList>
                    <div className={styles.tabs} id={"settings-tabs"}>
                        {selectedTab === tabIds.pairedOrganizations &&
                            <ParedOrganizationsTab
                                orgPairs={pairedOrgs}
                                onOrgPareUnpaired={() => fetchOrgPairs()}
                            />
                        }
                        {selectedTab === tabIds.paringRequests &&
                            <OrganizationParingRequestsTab
                                orgPairs={orgPairRequests}
                                onOrgPairRequestDeleted={onParingRequestDeleted}
                                onOrgPairRequestRejected={onParingRequestRejected}
                                onOrgPairRequestAccepted={onParingRequestAccepted}
                            />
                        }
                    </div>
                </div>
            </section>
            <ParingRequestDialog isOpen={isParingRequestDialogOpen}
                                 onDismiss={onParingRequestDialogDismiss}
                                 onRequestSent={onParingRequestSent}/>
        </>
        }
    </>
}

export default OrganizationPairingTab;
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
import {useOrganizationParingTabStyles} from "./ParingRequestsTabStyles.tsx";
import {DismissRegular, LinkAddRegular} from "@fluentui/react-icons";
import {OrganizationParingRequestsTabIcon, ParedOrganizationsTabIcon} from "../../components/IconBundles.tsx";
import ProfileTab from "../profile-tab/ProfileTab.tsx";
import OrganizationTab from "../organization-tab/OrganizationTab.tsx";

const ParingRequestsTab = () =>
{
    const tabIds = {
        pairedOrganizations: "PairedOrganizationsTab",
        paringRequests: "ParingRequestsTab"
    }

    const styles = useOrganizationParingTabStyles()
    const [fetchingOrganization, setFetchingOrganization] = useState(false);
    const [tabErrorMessage, setTabErrorMessage] = useState<string | null>(null);
    const [selectedValue, setSelectedValue] = useState<TabValue>(tabIds.pairedOrganizations);

    useEffect(() =>
    {
        console.log("Organization pairing requests tab useEffect");
    }, []);

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

    return <>
        {fetchingOrganization && <Spinner label="Loading"/>}

        {tabErrorMessage && renderTabError()}

        <section className={styles.container}>
            <Text size={500}> Paring Requests </Text>
        </section>
    </>
}

export default ParingRequestsTab;
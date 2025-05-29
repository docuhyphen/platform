import React, {useEffect, useState} from "react";
import {Button, MessageBar, MessageBarActions, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {useOrganizationParingTabStyles} from "./OrganizationParingTabStyles.tsx";
import {BuildingBankLinkRegular, DismissRegular, LinkRegular} from "@fluentui/react-icons";
import {GroupAddIcon} from "../../components/IconBundles.tsx";

const OrganizationPairingTab = () =>
{
    const styles = useOrganizationParingTabStyles()
    const [fetchingOrganization, setFetchingOrganization] = useState(false);
    const [tabErrorMessage, setTabErrorMessage] = useState<string | null>(null);

    useEffect(() =>
    {
        console.log("Organization pairing tab useEffect");
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

    const onInitiatePairing = () =>
    {

    }

    return <>
        {fetchingOrganization && <Spinner label="Loading"/>}

        {tabErrorMessage && renderTabError()}

        <div className={styles.header}>
            <div></div>

            <Button
                icon={<LinkRegular/>}
                appearance="primary"
                shape="circular"
                onClick={onInitiatePairing}>
                Find & Pair
            </Button>
        </div>
    </>
}

export default OrganizationPairingTab;
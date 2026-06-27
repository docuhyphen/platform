import React, {useEffect, useState} from "react";
import {
    Button,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Text,
    Table,
    TableHeader,
    TableRow,
    TableHeaderCell,
    TableBody,
    TableCell, Menu, MenuTrigger, MenuPopover, MenuList, MenuItem
} from "@fluentui/react-components";
import {useOrganizationParingTabStyles} from "./ParedOrganizationsTabStyles.tsx";
import {
    DismissRegular, LinkDismissRegular,
    MoreHorizontalRegular
} from "@fluentui/react-icons";
import {OrganizationExchangeLinkBasicDto} from "../../../models/models.tsx";
import useToken from "../../../../context/useToken";
import OrganizationUnpairDialog from "./organization-unpair-dialog/OrganizationUnpairDialog.tsx";

interface ParedOrganizationsTabProps
{
    orgPairs: OrganizationExchangeLinkBasicDto[]
    onOrgPareUnpaired: (orgPair: OrganizationExchangeLinkBasicDto) => void
}

const ParedOrganizationsTab: React.FC<ParedOrganizationsTabProps> = (
    {
        orgPairs,
        onOrgPareUnpaired
    }) =>
{
    const styles = useOrganizationParingTabStyles();
    const [tabErrorMessage, setTabErrorMessage] = useState<string | null>(null);
    const [unpairDialogOpen, setUnpairDialogOpen] = useState(false);
    const [selectedOrgPair, setSelectedOrgPair] = useState<OrganizationExchangeLinkBasicDto | null>(null);
    const token = useToken();

    const renderTabError = () => (
        tabErrorMessage && (
            <MessageBar intent={"error"}>
                <MessageBarBody>
                    <Text size={200}> {tabErrorMessage} </Text>
                </MessageBarBody>
                <MessageBarActions
                    containerAction={
                        <Button
                            id={"button-dismiss-tab-error"}
                            onClick={() => setTabErrorMessage(null)}
                            appearance="transparent"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        )
    );

    const onUnpair = (orgPair: OrganizationExchangeLinkBasicDto) =>
    {
        setSelectedOrgPair(orgPair);
        setUnpairDialogOpen(true);
    };

    const handleUnpairConfirmed = (orgPair: OrganizationExchangeLinkBasicDto) =>
    {
        setUnpairDialogOpen(false);
        setSelectedOrgPair(null);
        onOrgPareUnpaired(orgPair);
    };

    const handleUnpairCancelled = () =>
    {
        setUnpairDialogOpen(false);
        setSelectedOrgPair(null);
    };

    const renderActionsMenu = (orgPair: OrganizationExchangeLinkBasicDto) =>
    {
        return <>
            <Menu positioning={{autoSize: true}}>
                <MenuTrigger disableButtonEnhancement>
                    <Button
                        id={"button-org-pair-actions-menu"}
                        icon={<MoreHorizontalRegular/>}
                        shape={"circular"}
                        appearance={"subtle"}/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        <MenuItem
                            icon={<LinkDismissRegular/>}
                            onClick={() => onUnpair(orgPair)}>
                            Unpair
                        </MenuItem>
                    </MenuList>
                </MenuPopover>
            </Menu>
        </>
    }

    return (
        <>
            {tabErrorMessage && renderTabError()}

            <section className={styles.container}>
                {(!orgPairs || !orgPairs.length) &&
                    <Text>
                        You currently have no organizations you are paired with.
                    </Text>
                }
                {(orgPairs && orgPairs.length > 0) &&
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHeaderCell>Organization Name</TableHeaderCell>
                                <TableHeaderCell>Request Date</TableHeaderCell>
                                <TableHeaderCell>Status</TableHeaderCell>
                                <TableHeaderCell>Action</TableHeaderCell>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {orgPairs && orgPairs.map((orgPair) => (
                                <TableRow key={orgPair.id}>
                                    <TableCell>{orgPair.requestingOrganizationName}</TableCell>
                                    <TableCell>{orgPair.createdDate}</TableCell>
                                    <TableCell>{orgPair.status}</TableCell>
                                    <TableCell>
                                        {renderActionsMenu(orgPair)}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                }
            </section>
            <OrganizationUnpairDialog
                isOpen={unpairDialogOpen}
                orgPair={selectedOrgPair}
                onDismiss={handleUnpairCancelled}
                onUnpaired={handleUnpairConfirmed}
                setError={setTabErrorMessage}
                token={token}
            />
        </>
    );
};

export default ParedOrganizationsTab;
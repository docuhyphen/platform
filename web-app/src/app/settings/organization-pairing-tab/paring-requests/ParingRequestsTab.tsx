import React, {useEffect, useState} from "react";
import {
    Badge,
    Button,
    MessageBar,
    MessageBarActions,
    MessageBarBody,
    Text,
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableHeaderCell,
    TableRow,
    TableCellLayout, Divider, Menu, MenuTrigger, MenuPopover, MenuList, MenuItem
} from "@fluentui/react-components";
import {useOrganizationParingTabStyles} from "./ParingRequestsTabStyles.tsx";
import {
    CheckmarkRegular,
    DeleteRegular,
    DismissRegular,
    MoreHorizontalRegular,
} from "@fluentui/react-icons";
import {LinkStatus, OrganizationSharingSessionLinkBasicDto} from "../../../models/models.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {formatDate} from "../../../helpers.ts";
import ParingRequestDeleteDialog from "./paring-request-delete-dialog/ParingRequestDeleteDialog.tsx";
import ParingRequestAcceptDialog from "./paring-request-accept-dialog/ParingRequestAcceptDialog.tsx";
import ParingRequestRejectDialog from "./paring-request-reject-dialog/ParingRequestRejectDialog.tsx";

interface ParingOrganizationsTabProps
{
    orgPairs: OrganizationSharingSessionLinkBasicDto[]
    onOrgPairRequestDeleted: (orgPair: OrganizationSharingSessionLinkBasicDto) => void
    onOrgPairRequestRejected: (orgPair: OrganizationSharingSessionLinkBasicDto) => void
    onOrgPairRequestAccepted: (orgPair: OrganizationSharingSessionLinkBasicDto) => void
}

const ParingRequestsTab: React.FC<ParingOrganizationsTabProps> = (
    {
        orgPairs,
        onOrgPairRequestDeleted,
        onOrgPairRequestAccepted,
        onOrgPairRequestRejected
    }) =>
{
    const styles = useOrganizationParingTabStyles();
    const {appUserPersonOrganization} = useAuth();
    const [tabErrorMessage, setTabErrorMessage] = useState<string | null>(null);
    const [incomingOrgParingRequests, setIncomingOrgParingRequests] = useState<OrganizationSharingSessionLinkBasicDto[]>([]);
    const [outgoingOrgParingRequests, setOutgoingOrgParingRequests] = useState<OrganizationSharingSessionLinkBasicDto[]>([]);
    const [isOrgPairRequestDeleteDialogOpen, setIsOrgPairRequestDeleteDialogOpen] = useState<boolean>(false);
    const [isOrgPairRequestAcceptDialogOpen, setIsOrgPairRequestAcceptDialogOpen] = useState<boolean>(false);
    const [isOrgPairRequestRejectDialogOpen, setIsOrgPairRequestRejectDialogOpen] = useState<boolean>(false);
    const [currentOrgPairRequest, setCurrentOrgPairRequest] = useState<OrganizationSharingSessionLinkBasicDto | null>(null);

    const columns = [
        {columnKey: "organizationName", label: "Organization Name"},
        {columnKey: "requestDate", label: "Request Date"},
        {columnKey: "requestingMessage", label: "Request Message"},
        {columnKey: "status", label: "Status"},
        {columnKey: "actions", label: "Action"}
    ];

    useEffect(() =>
    {
        setIncomingOrgParingRequests(
            orgPairs.filter(p => p.requestedOrganizationId === appUserPersonOrganization?.id))

        setOutgoingOrgParingRequests(
            orgPairs.filter(p => p.requestingOrganizationId === appUserPersonOrganization?.id))

    }, [orgPairs, appUserPersonOrganization]);

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

    const onDeleteOrgPairRequest = async (orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        setIsOrgPairRequestDeleteDialogOpen(true)
        setCurrentOrgPairRequest(orgPair)
    }

    const onOrgPairDeleted = (orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        setIsOrgPairRequestDeleteDialogOpen(false)
        onOrgPairRequestDeleted(orgPair)
    }

    const onAcceptRequest = async (orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        setCurrentOrgPairRequest(orgPair)
        setIsOrgPairRequestAcceptDialogOpen(true)
    }

    const onOrgPairAccepted = (orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        setIsOrgPairRequestAcceptDialogOpen(false)
        onOrgPairRequestAccepted(orgPair)
    }

    const onRejectRequest = async (orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        setCurrentOrgPairRequest(orgPair)
        setIsOrgPairRequestRejectDialogOpen(true)
    }

    const onOrgPairRejected = (orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        setIsOrgPairRequestRejectDialogOpen(false)
        onOrgPairRequestRejected(orgPair)
    }

    const renderTableActionsMenu = (
        isOutgoingOrgRequests: boolean,
        orgPair: OrganizationSharingSessionLinkBasicDto) =>
    {
        return <>
            <Menu positioning={{autoSize: true}}>
                <MenuTrigger disableButtonEnhancement>
                    <Button icon={<MoreHorizontalRegular/>}
                            appearance={"subtle"}/>
                </MenuTrigger>
                <MenuPopover>
                    <MenuList>
                        {isOutgoingOrgRequests &&
                            <MenuItem
                                icon={<DeleteRegular/>}
                                onClick={() => onDeleteOrgPairRequest(orgPair)}>
                                Delete Request
                            </MenuItem>
                        }
                        {!isOutgoingOrgRequests && orgPair.status === LinkStatus.PENDING && <>
                            <MenuItem
                                icon={<CheckmarkRegular/>}
                                onClick={() => onAcceptRequest(orgPair)}>
                                Accept
                            </MenuItem>
                            <MenuItem
                                icon={<DismissRegular/>}
                                onClick={() => onRejectRequest(orgPair)}>
                                Reject
                            </MenuItem>
                        </>
                        }
                        {!isOutgoingOrgRequests && orgPair.status === LinkStatus.REJECTED && <>
                            <MenuItem>
                                No further actions needed
                            </MenuItem>
                        </>
                        }
                    </MenuList>
                </MenuPopover>
            </Menu>
        </>
    };

    const renderTable = (isOutgoingOrgRequests: boolean) =>
    {
        return <>
            <Table aria-label="Requests to other organizations">
                <TableHeader>
                    <TableRow>
                        {columns.map(column => (
                            <TableHeaderCell key={column.columnKey}>
                                <Text weight={"semibold"}> {column.label} </Text>
                            </TableHeaderCell>
                        ))}
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {orgPairs
                        .filter(orgPair =>
                        {
                            if (isOutgoingOrgRequests)
                            {
                                return orgPair.requestingOrganizationId === appUserPersonOrganization?.id
                            }
                            else
                            {
                                return orgPair.requestedOrganizationId === appUserPersonOrganization?.id
                            }
                        })
                        .map((orgPair, index) => (
                            <TableRow key={`og-pair-request-tow-${index}`}>
                                <TableCell>
                                    <TableCellLayout>
                                        {isOutgoingOrgRequests && orgPair.requestedOrganizationName}
                                        {!isOutgoingOrgRequests && orgPair.requestingOrganizationName}
                                    </TableCellLayout>
                                </TableCell>
                                <TableCell>
                                    <TableCellLayout>
                                        {formatDate(orgPair.createdDate)}
                                    </TableCellLayout>
                                </TableCell>
                                <TableCell>
                                    <TableCellLayout>
                                        <Text italic>{orgPair.requestingMessage || "No request message"}</Text>
                                    </TableCellLayout>
                                </TableCell>
                                <TableCell>
                                    <TableCellLayout>
                                        <Badge
                                            color={orgPair.status === LinkStatus.PENDING ? "warning" : "danger"}
                                            appearance="outline"
                                            size={"small"}>
                                            {orgPair.status}
                                        </Badge>
                                    </TableCellLayout>
                                </TableCell>
                                <TableCell>
                                    <TableCellLayout>
                                        {renderTableActionsMenu(isOutgoingOrgRequests, orgPair)}
                                    </TableCellLayout>
                                </TableCell>
                            </TableRow>
                        ))}
                </TableBody>
            </Table>
        </>
    }

    return <section className={styles.tabContainer}>

        {tabErrorMessage && renderTabError()}

        {(orgPairs && orgPairs.length > 0) && <>
            {(outgoingOrgParingRequests && outgoingOrgParingRequests.length > 0) && <>
                <Divider alignContent={"start"}
                         appearance={"brand"}
                         className={styles.divider}>
                    Paring requests to other organizations
                </Divider>
                {renderTable(true)}
            </>
            }
            {(incomingOrgParingRequests && incomingOrgParingRequests.length > 0) && <>
                <Divider alignContent={"start"}
                         appearance={"brand"}
                         className={styles.divider}>
                    Paring requests from other organizations
                </Divider>
                {renderTable(false)}
            </>
            }
        </>
        }

        {(!orgPairs || orgPairs.length < 1) &&
            <Text> You currently have no requests sent to or from other organizations</Text>
        }
        <ParingRequestDeleteDialog
            orgPair={currentOrgPairRequest}
            isOpen={isOrgPairRequestDeleteDialogOpen}
            onDismiss={() => setIsOrgPairRequestDeleteDialogOpen(false)}
            onDeleted={onOrgPairDeleted}/>

        <ParingRequestAcceptDialog
            orgPair={currentOrgPairRequest}
            isOpen={isOrgPairRequestAcceptDialogOpen}
            onDismiss={() => setIsOrgPairRequestAcceptDialogOpen(false)}
            onAccepted={onOrgPairAccepted}/>

        <ParingRequestRejectDialog
            orgPair={currentOrgPairRequest}
            isOpen={isOrgPairRequestRejectDialogOpen}
            onDismiss={() => setIsOrgPairRequestRejectDialogOpen(false)}
            onRejected={onOrgPairRejected}/>
    </section>
}

export default ParingRequestsTab;
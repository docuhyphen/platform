import React, {useEffect} from 'react';
import {
    Body1,
    Button,
    Caption1,
    Divider,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Text,
    Tooltip
} from "@fluentui/react-components";
import {MoreVerticalRegular} from "@fluentui/react-icons";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {SharingSessionDetailedDto, SharingSessionStatus} from "../../../models/models.tsx";
import {useSessionDetailsHeaderStyles} from "./SessionDetailsHeaderStyles.tsx";
import {
    DeleteIcon,
    DocumentAddIcon,
    EditSessionIcon,
    ManageAccessIcon,
    SessionDetailedViewIcon,
    SessionEndIcon,
    ToggleHeaderDownIcon,
    ToggleHeaderUpIcon
} from "../../../components/IconBundles.tsx";
import {SharingSessionPermissions} from "../../SessionPermissions.ts";

interface SessionDetailsHeaderProps
{
    sessionDetails: SharingSessionDetailedDto | null;
    setIsDocumentAddDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsSessionEndDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsDeletedSessionDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsSessionDetailedViewDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsSessionEditDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsSessionAccessManagementDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    sessionPermissions: SharingSessionPermissions;
}

const SessionDetailsHeader: React.FC<SessionDetailsHeaderProps> = (
    {
        sessionDetails,
        setIsDocumentAddDialogOpen,
        setIsSessionEndDialogOpen,
        setIsSessionDetailedViewDialogOpen,
        setIsDeletedSessionDialogOpen,
        setIsSessionEditDialogOpen,
        setIsSessionAccessManagementDialogOpen,
        sessionPermissions
    }) =>
{
    const styles = useSessionDetailsHeaderStyles();
    const [isCollapsed, setIsCollapsed] = React.useState(false);

    useEffect(() =>
    {
        setIsCollapsed(false);
    }, []);

    const toggleHeaderDetails = () =>
    {
        setIsCollapsed((prev) => !prev);
    };

    const getSessionHeadContainerClass = () =>
    {
        return `${styles.container} ${styles[`containerStatus${sessionDetails?.status || ''}` as keyof typeof styles]}`;
    };

    return (
        <section className={getSessionHeadContainerClass()}>
            {sessionDetails && (
                <div className={styles.header}>
                    {isCollapsed &&
                        <div className={styles.headerLine1}>
                            <div className={styles.headerLine1_2} id={"session-details-header-l1-1"}>
                                <Caption1>
                                    Started {formatDateTimeWithOrdinal(sessionDetails.createdDate)}
                                </Caption1>
                                {sessionDetails.endDate && (
                                    <>
                                        <Caption1>|</Caption1>
                                        <Caption1>
                                            Ended {formatDateTimeWithOrdinal(sessionDetails.createdDate)}
                                        </Caption1>
                                    </>
                                )}
                            </div>

                            <Button
                                onClick={toggleHeaderDetails}
                                size={"small"}
                                appearance={"subtle"}
                                icon={<ToggleHeaderUpIcon/>}/>
                        </div>
                    }
                    <div className={styles.headerLine2}>
                        <Text size={isCollapsed ? 600 : 500}>{sessionDetails.sessionName}</Text>
                        <div className={styles.actions}>
                            <Tooltip content="Add Session Document" relationship="description">
                                <Button
                                    icon={<DocumentAddIcon/>}
                                    appearance="primary"
                                    disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canAddSessionDocument}
                                    onClick={() => setIsDocumentAddDialogOpen(true)}
                                />
                            </Tooltip>
                            <Tooltip content="Edit" relationship="description">
                                <Button
                                    icon={<EditSessionIcon/>}
                                    disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEditSessionDocument}
                                    appearance={"subtle"}
                                    onClick={() => setIsSessionEditDialogOpen(true)}
                                />
                            </Tooltip>
                            <Tooltip content="manage access" relationship="description">
                                <Button
                                    icon={<ManageAccessIcon/>}
                                    disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEditSharingOptions}
                                    appearance={"subtle"}
                                    onClick={() => setIsSessionAccessManagementDialogOpen(true)}
                                />
                            </Tooltip>
                            <Menu positioning={{autoSize: true}}>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button icon={<MoreVerticalRegular/>} appearance="subtle"/>
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList>
                                        <MenuItem
                                            icon={<SessionDetailedViewIcon/>}
                                            onClick={() => setIsSessionDetailedViewDialogOpen(true)}>
                                            More info
                                        </MenuItem>
                                        <Divider/>
                                        {sessionDetails.status === SharingSessionStatus.ACCEPTED_STARTED

                                        }
                                        <MenuItem
                                            icon={<SessionEndIcon/>}
                                            disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEndSession}
                                            onClick={() => setIsSessionEndDialogOpen(true)}>
                                            End
                                        </MenuItem>
                                        <MenuItem
                                            icon={<DeleteIcon/>}
                                            disabled={!sessionPermissions.canDeleteSession}
                                            onClick={() => setIsDeletedSessionDialogOpen(true)}>
                                            Delete
                                        </MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>

                            {!isCollapsed && <Button
                                onClick={toggleHeaderDetails}
                                size={"small"}
                                appearance={"subtle"}
                                icon={<ToggleHeaderDownIcon/>}/>
                            }
                        </div>
                    </div>
                    {isCollapsed && (
                        <div className={styles.headerLine3}>
                            <Body1>{sessionDetails.description}</Body1>
                        </div>
                    )}
                </div>
            )}
        </section>
    );
};

export default SessionDetailsHeader;

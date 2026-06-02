import React, {useEffect} from 'react';
import {
    Body1,
    Button,
    Caption2,
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
import {ArrowLeftRegular} from "@fluentui/react-icons";
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
import {useMediaQuery} from "../../../../utils/useMediaQuery.ts";

const HEADER_EXPANDED_STORAGE_KEY = 'sharingSessions.header.isExpanded';

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
    onRecreateRejectedSession: (session: SharingSessionDetailedDto) => void;
    /**
     * Provided only on mobile viewports where the list and details panes
     * are mutually exclusive. When set, a back arrow is rendered before
     * the session name so the user can return to the SessionList.
     */
    onBackToList?: () => void;
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
        sessionPermissions,
        onRecreateRejectedSession,
        onBackToList,
    }) =>
{
    const styles = useSessionDetailsHeaderStyles();

    // TEMP DEBUG — remove once permissions issue resolved
    // eslint-disable-next-line no-console
    console.log('[SessionDetailsHeader]', {
        sessionId: sessionDetails?.id,
        status: sessionDetails?.status,
        sessionPermissions,
    });
    // Collapse the header's standalone action icons into the More menu
    // on any compact viewport - not just phones. Tablet portrait
    // (768-1024px) still has the SessionList sidebar competing for
    // horizontal room, so the title + 3 icon buttons + menu + toggle
    // doesn't fit comfortably either.
    const collapseHeaderActionsToMenu = useMediaQuery('(max-width: 1024px)');
    const [isExpanded, setIsExpanded] = React.useState(() =>
    {
        if (typeof window === 'undefined') return false;
        const saved = window.localStorage.getItem(HEADER_EXPANDED_STORAGE_KEY);
        return saved === 'true';
    });

    useEffect(() =>
    {
        if (typeof window === 'undefined') return;
        window.localStorage.setItem(HEADER_EXPANDED_STORAGE_KEY, String(isExpanded));
    }, [isExpanded]);

    const toggleHeaderDetails = () =>
    {
        setIsExpanded((prev) => !prev);
    };

    const detailsToggleTooltip = isExpanded ? "Collapse details" : "Expand details";

    const getSessionHeadContainerClass = () =>
    {
        return `${styles.container} ${styles[`containerStatus${sessionDetails?.status || ''}` as keyof typeof styles]}`;
    };

    return (
        <section className={getSessionHeadContainerClass()}>
            {sessionDetails && (
                <div className={styles.header}>
                    <div
                        className={styles.headerAnimatedSection}
                        style={{
                            maxHeight: isExpanded ? 64 : 0,
                            opacity: isExpanded ? 1 : 0,
                            marginTop: isExpanded ? 4 : 0,
                        }}>
                        <div className={styles.headerLine1}>
                            <div className={styles.headerLine1_2} id={"session-details-header-l1-1"}>
                                <Caption2>
                                    Started {formatDateTimeWithOrdinal(sessionDetails.createdDate)}
                                </Caption2>
                                {sessionDetails.endDate && (
                                    <>
                                        <Caption2>|</Caption2>
                                        <Caption2>
                                            Ended {formatDateTimeWithOrdinal(sessionDetails.createdDate)}
                                        </Caption2>
                                    </>
                                )}
                            </div>
                        </div>
                    </div>
                    <div className={styles.headerLine2}>
                        <div className={styles.headerTitleGroup}>
                            {onBackToList && (
                                <Tooltip content="Back to sessions" relationship="description">
                                    <Button
                                        id="session-details-header-back-to-list"
                                        size="small"
                                        appearance="subtle"
                                        shape="circular"
                                        aria-label="Back to sessions"
                                        icon={<ArrowLeftRegular/>}
                                        onClick={onBackToList}
                                        className={styles.backToListButton}
                                    />
                                </Tooltip>
                            )}
                            <div className={styles.headerTitleText}>
                                {/*
                                  Wrapping Text in a block-level div lets
                                  the flex chain (`flex: 1` + `min-width: 0`
                                  on the wrapper) actually shrink the title
                                  on narrow viewports. The Text itself is
                                  a <span> (inline) so flex shrinking does
                                  not apply directly to it.
                                */}
                                <Text
                                    size={isExpanded ? 500 : 600}
                                    truncate
                                    wrap={false}
                                    title={sessionDetails.sessionName}>
                                    {sessionDetails.sessionName}
                                </Text>
                            </div>
                        </div>
                        <div className={styles.actions}>
                            {!collapseHeaderActionsToMenu && (
                                <>
                                    <Tooltip content="Add Session Document" relationship="description">
                                        <Button
                                            id="session-details-header-add-document"
                                            icon={<DocumentAddIcon/>}
                                            appearance="primary"
                                            disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canAddSessionDocument}
                                            onClick={() => setIsDocumentAddDialogOpen(true)}
                                        />
                                    </Tooltip>
                                    <Tooltip content="Edit" relationship="description">
                                        <Button
                                            id="session-details-header-edit-session"
                                            icon={<EditSessionIcon/>}
                                            disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEditSharingOptions}
                                            appearance={"subtle"}
                                            onClick={() => setIsSessionEditDialogOpen(true)}
                                        />
                                    </Tooltip>
                                    <Tooltip content="manage access" relationship="description">
                                        <Button
                                            id="session-details-header-manage-access"
                                            icon={<ManageAccessIcon/>}
                                            disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEditSharingOptions}
                                            appearance={"subtle"}
                                            onClick={() => setIsSessionAccessManagementDialogOpen(true)}
                                        />
                                    </Tooltip>
                                </>
                            )}
                            <Menu positioning={{autoSize: true}}>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button id="session-details-header-more-menu-trigger" icon={<MoreVerticalRegular/>} appearance="subtle"/>
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList id="session-details-header-more-menu-list">
                                        {/*
                                          On compact viewports (tablet portrait and
                                          smaller) the Add / Edit / Manage Access
                                          buttons are folded into this menu instead
                                          of being rendered as separate icon buttons
                                          in the header.
                                        */}
                                        {collapseHeaderActionsToMenu && (
                                            <>
                                                <MenuItem
                                                    id="session-details-header-menu-add-document"
                                                    icon={<DocumentAddIcon/>}
                                                    disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canAddSessionDocument}
                                                    onClick={() => setIsDocumentAddDialogOpen(true)}>
                                                    Add document
                                                </MenuItem>
                                                <MenuItem
                                                    id="session-details-header-menu-edit-session"
                                                    icon={<EditSessionIcon/>}
                                                    disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEditSharingOptions}
                                                    onClick={() => setIsSessionEditDialogOpen(true)}>
                                                    Edit session
                                                </MenuItem>
                                                <MenuItem
                                                    id="session-details-header-menu-manage-access"
                                                    icon={<ManageAccessIcon/>}
                                                    disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEditSharingOptions}
                                                    onClick={() => setIsSessionAccessManagementDialogOpen(true)}>
                                                    Manage access
                                                </MenuItem>
                                                <Divider/>
                                            </>
                                        )}
                                        <MenuItem
                                            id="session-details-header-menu-more-info"
                                            icon={<SessionDetailedViewIcon/>}
                                            onClick={() => setIsSessionDetailedViewDialogOpen(true)}>
                                            More info
                                        </MenuItem>
                                        {sessionDetails.status === SharingSessionStatus.REJECTED && sessionPermissions.canDeleteSession && (
                                            <MenuItem
                                                id="session-details-header-menu-recreate-request"
                                                icon={<EditSessionIcon/>}
                                                onClick={() => onRecreateRejectedSession(sessionDetails)}>
                                                Recreate Request
                                            </MenuItem>
                                        )}
                                        <Divider/>
                                        {sessionDetails.status === SharingSessionStatus.ACCEPTED_STARTED

                                        }
                                        <MenuItem
                                            id="session-details-header-menu-end"
                                            icon={<SessionEndIcon/>}
                                            disabled={sessionDetails.status === SharingSessionStatus.ENDED || !sessionPermissions.canEndSession}
                                            onClick={() => setIsSessionEndDialogOpen(true)}>
                                            End
                                        </MenuItem>
                                        <MenuItem
                                            id="session-details-header-menu-delete"
                                            icon={<DeleteIcon/>}
                                            disabled={!sessionPermissions.canDeleteSession}
                                            onClick={() => setIsDeletedSessionDialogOpen(true)}>
                                            Delete
                                        </MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>

                            <Tooltip content={detailsToggleTooltip} relationship="description">
                                <Button
                                    id="session-details-header-toggle-details"
                                    onClick={toggleHeaderDetails}
                                    size={"small"}
                                    appearance={"subtle"}
                                    aria-label={detailsToggleTooltip}
                                    icon={isExpanded ? <ToggleHeaderUpIcon/> : <ToggleHeaderDownIcon/>}/>
                            </Tooltip>
                        </div>
                    </div>
                    <div
                        className={styles.headerAnimatedSection}
                        style={{
                            maxHeight: isExpanded ? 96 : 0,
                            opacity: isExpanded ? 1 : 0,
                            marginTop: isExpanded ? 4 : 0,
                        }}>
                        <div className={styles.headerLine3}>
                            <Body1>{sessionDetails.description}</Body1>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
};

export default SessionDetailsHeader;

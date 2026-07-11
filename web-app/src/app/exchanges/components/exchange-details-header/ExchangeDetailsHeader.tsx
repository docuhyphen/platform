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
import {ExchangeDetailedDto, ExchangeStatus} from "../../../models/models.tsx";
import {useExchangeDetailsHeaderStyles} from "./ExchangeDetailsHeaderStyles.tsx";
import {
    DeleteIcon,
    DocumentAddIcon,
    EditExchangeIcon,
    ManageAccessIcon,
    ExchangeDetailedViewIcon,
    ExchangeEndIcon,
    ToggleHeaderDownIcon,
    ToggleHeaderUpIcon
} from "../../../components/IconBundles.tsx";
import {ExchangePermissions} from "../../ExchangePermissions.ts";
import {useMediaQuery} from "../../../../utils/useMediaQuery.ts";

const HEADER_EXPANDED_STORAGE_KEY = 'exchanges.header.isExpanded';

interface ExchangeDetailsHeaderProps
{
    exchangeDetails: ExchangeDetailedDto | null;
    setIsDocumentAddDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeEndDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeRescindDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsDeletedExchangeDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeDetailedViewDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeEditDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    setIsExchangeAccessManagementDialogOpen: React.Dispatch<React.SetStateAction<boolean>>;
    exchangePermissions: ExchangePermissions;
    onRecreateRejectedExchange: (exchange: ExchangeDetailedDto) => void;
    /**
     * Provided only on mobile viewports where the list and details panes
     * are mutually exclusive. When set, a back arrow is rendered before
     * the exchange name so the user can return to the ExchangeList.
     */
    onBackToList?: () => void;
}

const ExchangeDetailsHeader: React.FC<ExchangeDetailsHeaderProps> = (
    {
        exchangeDetails,
        setIsDocumentAddDialogOpen,
        setIsExchangeEndDialogOpen,
        setIsExchangeRescindDialogOpen,
        setIsExchangeDetailedViewDialogOpen,
        setIsDeletedExchangeDialogOpen,
        setIsExchangeEditDialogOpen,
        setIsExchangeAccessManagementDialogOpen,
        exchangePermissions,
        onRecreateRejectedExchange,
        onBackToList,
    }) =>
{
    const styles = useExchangeDetailsHeaderStyles();

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

    const getExchangeHeadContainerClass = () =>
    {
        return `${styles.container} ${styles[`containerStatus${exchangeDetails?.status || ''}` as keyof typeof styles]}`;
    };

    const getAnimatedSectionClass = (large = false) =>
    {
        const expandedClass = large
            ? styles.headerAnimatedSectionExpandedLarge
            : styles.headerAnimatedSectionExpanded;
        return `${styles.headerAnimatedSection} ${isExpanded ? expandedClass : ''}`;
    };

    return (
        <section className={getExchangeHeadContainerClass()}>
            {exchangeDetails && (
                <div className={styles.header}>
                    <div
                        className={getAnimatedSectionClass()}>
                        <div className={styles.headerLine1}>
                            <div className={styles.headerLine1_2} id={"exchange-details-header-l1-1"}>
                                <Caption2>
                                    Started {formatDateTimeWithOrdinal(exchangeDetails.createdDate)}
                                </Caption2>
                                {exchangeDetails.endDate && (
                                    <>
                                        <Caption2>|</Caption2>
                                        <Caption2>
                                            Ended {formatDateTimeWithOrdinal(exchangeDetails.createdDate)}
                                        </Caption2>
                                    </>
                                )}
                            </div>
                        </div>
                    </div>
                    <div className={styles.headerLine2}>
                        <div className={styles.headerTitleGroup}>
                            {onBackToList && (
                                <Tooltip content="Back to exchanges" relationship="description">
                                    <Button
                                        id="exchange-details-header-back-to-list"
                                        size="small"
                                        appearance="subtle"
                                        shape="circular"
                                        aria-label="Back to exchanges"
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
                                    title={exchangeDetails.name}>
                                    {exchangeDetails.name}
                                </Text>
                            </div>
                        </div>
                        <div className={styles.actions}>
                            {!collapseHeaderActionsToMenu && (
                                <>
                                    <Tooltip content="Add Exchange Document" relationship="description">
                                        <Button
                                            id="exchange-details-header-add-document"
                                            icon={<DocumentAddIcon/>}
                                            appearance="primary"
                                            shape={"circular"}
                                            disabled={
                                                exchangeDetails.status === ExchangeStatus.ENDED ||
                                                exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                !exchangePermissions.canAddExchangeDocument
                                            }
                                            onClick={() => setIsDocumentAddDialogOpen(true)}
                                        />
                                    </Tooltip>
                                    <Tooltip content="Edit" relationship="description">
                                        <Button
                                            id="exchange-details-header-edit-exchange"
                                            icon={<EditExchangeIcon/>}
                                            disabled={
                                                exchangeDetails.status === ExchangeStatus.ENDED ||
                                                exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                !exchangePermissions.canEditSharingOptions
                                            }
                                            appearance={"subtle"}
                                            shape={"circular"}
                                            onClick={() => setIsExchangeEditDialogOpen(true)}
                                        />
                                    </Tooltip>
                                    <Tooltip content="manage access" relationship="description">
                                        <Button
                                            id="exchange-details-header-manage-access"
                                            icon={<ManageAccessIcon/>}
                                            disabled={
                                                exchangeDetails.status === ExchangeStatus.ENDED ||
                                                exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                !exchangePermissions.canEditSharingOptions
                                            }
                                            appearance={"subtle"}
                                            shape={"circular"}
                                            onClick={() => setIsExchangeAccessManagementDialogOpen(true)}
                                        />
                                    </Tooltip>
                                </>
                            )}
                            <Menu positioning={{autoSize: true}}>
                                <MenuTrigger disableButtonEnhancement>
                                    <Button
                                        id="exchange-details-header-more-menu-trigger"
                                        icon={<MoreVerticalRegular/>}
                                        appearance="subtle"
                                        shape={"circular"}
                                    />
                                </MenuTrigger>
                                <MenuPopover>
                                    <MenuList id="exchange-details-header-more-menu-list">
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
                                                    id="exchange-details-header-menu-add-document"
                                                    icon={<DocumentAddIcon/>}
                                                    disabled={
                                                        exchangeDetails.status === ExchangeStatus.ENDED ||
                                                        exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                        !exchangePermissions.canAddExchangeDocument
                                                    }
                                                    onClick={() => setIsDocumentAddDialogOpen(true)}>
                                                    Add document
                                                </MenuItem>
                                                <MenuItem
                                                    id="exchange-details-header-menu-edit-exchange"
                                                    icon={<EditExchangeIcon/>}
                                                    disabled={
                                                        exchangeDetails.status === ExchangeStatus.ENDED ||
                                                        exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                        !exchangePermissions.canEditSharingOptions
                                                    }
                                                    onClick={() => setIsExchangeEditDialogOpen(true)}>
                                                    Edit exchange
                                                </MenuItem>
                                                <MenuItem
                                                    id="exchange-details-header-menu-manage-access"
                                                    icon={<ManageAccessIcon/>}
                                                    disabled={
                                                        exchangeDetails.status === ExchangeStatus.ENDED ||
                                                        exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                        !exchangePermissions.canEditSharingOptions
                                                    }
                                                    onClick={() => setIsExchangeAccessManagementDialogOpen(true)}>
                                                    Manage access
                                                </MenuItem>
                                                <Divider/>
                                            </>
                                        )}
                                        <MenuItem
                                            id="exchange-details-header-menu-more-info"
                                            icon={<ExchangeDetailedViewIcon/>}
                                            onClick={() => setIsExchangeDetailedViewDialogOpen(true)}>
                                            More info
                                        </MenuItem>
                                        {exchangeDetails.status === ExchangeStatus.REJECTED && exchangePermissions.canDeleteExchange && (
                                            <MenuItem
                                                id="exchange-details-header-menu-recreate-request"
                                                icon={<EditExchangeIcon/>}
                                                onClick={() => onRecreateRejectedExchange(exchangeDetails)}>
                                                Recreate Request
                                            </MenuItem>
                                        )}
                                        <Divider/>
                                        {exchangeDetails.status === ExchangeStatus.ACCEPTED_STARTED

                                        }
                                        <MenuItem
                                            id="exchange-details-header-menu-rescind"
                                            icon={<DeleteIcon/>}
                                            disabled={
                                                (
                                                    exchangeDetails.status !== ExchangeStatus.INITIATED &&
                                                    exchangeDetails.status !== ExchangeStatus.ACCEPTED_STARTED
                                                ) ||
                                                !exchangePermissions.canDeleteExchange
                                            }
                                            onClick={() => setIsExchangeRescindDialogOpen(true)}>
                                            Rescind
                                        </MenuItem>
                                        <MenuItem
                                            id="exchange-details-header-menu-end"
                                            icon={<ExchangeEndIcon/>}
                                            disabled={
                                                exchangeDetails.status === ExchangeStatus.ENDED ||
                                                exchangeDetails.status === ExchangeStatus.RESCINDED ||
                                                !exchangePermissions.canEndExchange
                                            }
                                            onClick={() => setIsExchangeEndDialogOpen(true)}>
                                            End
                                        </MenuItem>
                                        <MenuItem
                                            id="exchange-details-header-menu-delete"
                                            icon={<DeleteIcon/>}
                                            disabled={!exchangePermissions.canDeleteExchange}
                                            onClick={() => setIsDeletedExchangeDialogOpen(true)}>
                                            Delete
                                        </MenuItem>
                                    </MenuList>
                                </MenuPopover>
                            </Menu>

                            <Tooltip content={detailsToggleTooltip} relationship="description">
                                <Button
                                    id="exchange-details-header-toggle-details"
                                    onClick={toggleHeaderDetails}
                                    size={"small"}
                                    appearance={"subtle"}
                                    shape={"circular"}
                                    aria-label={detailsToggleTooltip}
                                    icon={isExpanded ? <ToggleHeaderUpIcon/> : <ToggleHeaderDownIcon/>}/>
                            </Tooltip>
                        </div>
                    </div>
                    <div
                        className={getAnimatedSectionClass(true)}>
                        <div className={styles.headerLine3}>
                            <Body1>{exchangeDetails.description}</Body1>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
};

export default ExchangeDetailsHeader;

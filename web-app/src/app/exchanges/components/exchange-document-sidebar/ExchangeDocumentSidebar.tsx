import React from "react";
import {
    Button,
    DrawerHeader,
    DrawerHeaderTitle,
    InlineDrawer,
    OverlayDrawer,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    mergeClasses,
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {useExchangeDocumentSidebarStyles} from "./ExchangeDocumentSidebarStyles.tsx";
import {Capability, DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import {AuditIcon, CommentIcon, DocumentVersionsIcon} from "../../../components/IconBundles.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {getPermissions} from "../../ExchangePermissions.ts";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import ExchangeDocumentMetadata from "./document-metadata/ExchangeDocumentMetadata.tsx";
import ExchangeDocumentSidebarBody from "./sidebar-body/ExchangeDocumentSidebarBody.tsx";

interface ExchangeDocumentSidebarProps
{
    onOpen: (open: boolean) => void;
    isOpen: boolean;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
    pageNumber?: number;
    onNavigateToPage?: (pageNumber: number) => void;
}

const ExchangeDocumentSidebar: React.FC<ExchangeDocumentSidebarProps> = (
    {
        onOpen,
        isOpen,
        exchangeDocument,
        exchange,
        pageNumber,
        onNavigateToPage,
    }) =>
{
    const [selectedValue, setSelectedValue] = React.useState<TabValue>("comments");
    const {appUser, hasCapability} = useAuth();
    const permissions = getPermissions(exchange, appUser);
    const canViewAudit = hasCapability(Capability.ORG_AUDIT_READ);
    const styles = useExchangeDocumentSidebarStyles();
    const isMobile = useIsMobile();

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };


    // On phones we don't have room for an inline 400px-wide aside next to
    // the document previewer, so we promote the sidebar to a modal-style
    // OverlayDrawer that slides in over the viewport. Desktop keeps the
    // existing inline behavior so the previewer + sidebar are visible
    // side-by-side.
    const DrawerComponent = isMobile ? OverlayDrawer : InlineDrawer;
    const drawerProps = isMobile
        ? {
            size: "full" as const,
            onOpenChange: (_: unknown, data: {open: boolean}) =>
            {
                if (!data.open) onOpen(false);
            },
        }
        : {};

    return (
        <DrawerComponent
            as={"aside"}
            id={"ExchangeDocumentSidebar"}
            open={isOpen}
            className={mergeClasses(styles.sidebarContainer, isMobile && styles.sidebarContainerMobile)}
            position={"end"}
            {...drawerProps}
        >
            <DrawerHeader
                id={"exchange-document-sidebar-header"}
                className={styles.drawerHeader}
            >
                <DrawerHeaderTitle
                    id={"exchange-document-sidebar-header-title"}
                    className={styles.drawerHeaderTitle}
                    action={
                        <Button
                            id={"exchange-document-sidebar-dismiss-btn"}
                            size={"small"}
                            appearance={"subtle"}
                            shape={"circular"}
                            icon={<DismissRegular/>}
                            onClick={() => onOpen(false)}
                        />
                    }>
                    <div
                        id={"exchange-document-sidebar-header-content"}
                        className={styles.headerContent}
                    >
                        <ExchangeDocumentMetadata document={exchangeDocument}/>

                        <TabList
                            id={"exchange-document-sidebar-tabs"}
                            selectedValue={selectedValue}
                            onTabSelect={onTabSelect}
                        >
                            <Tab
                                id={"comments"}
                                icon={<CommentIcon/>}
                                value={"comments"}
                            >
                                Notes/Comments
                            </Tab>
                            <Tab
                                id={"versions"}
                                icon={<DocumentVersionsIcon/>}
                                value={"versions"}
                            >
                                Versions
                            </Tab>
                            {canViewAudit && (
                                <Tab
                                    id={"audit"}
                                    icon={<AuditIcon/>}
                                    value={"audit"}
                                >
                                    Audit
                                </Tab>
                            )}
                        </TabList>
                    </div>
                </DrawerHeaderTitle>
            </DrawerHeader>
            <ExchangeDocumentSidebarBody
                selectedValue={selectedValue}
                exchangeDocument={exchangeDocument}
                exchange={exchange}
                canViewAudit={canViewAudit}
                canUpload={permissions.canUploadDocument}
                canDownload={permissions.canDownloadDocumentsZip}
                pageNumber={pageNumber}
                onNavigateToPage={onNavigateToPage}
            />
        </DrawerComponent>
    );
};

export default ExchangeDocumentSidebar;

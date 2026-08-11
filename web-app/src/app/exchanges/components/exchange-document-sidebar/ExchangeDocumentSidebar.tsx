import React from "react";
import {
    Button,
    DrawerHeader,
    DrawerHeaderTitle,
    InlineDrawer,
    OverlayDrawer,
    SelectTabData,
    SelectTabEvent,
    TabValue,
    mergeClasses,
} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";
import {useExchangeDocumentSidebarStyles} from "./ExchangeDocumentSidebarStyles.tsx";
import {Capability, DocumentDetailedDto, ExchangeDetailedDto, PlanFeature} from "../../../models/models.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {getPermissions} from "../../ExchangePermissions.ts";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";
import ExchangeDocumentMetadata from "./document-metadata/ExchangeDocumentMetadata.tsx";
import ExchangeDocumentSidebarBody from "./sidebar-body/ExchangeDocumentSidebarBody.tsx";
import {usePlanFeature} from "../../../../hooks/subscription/usePlanFeature.ts";
import ExchangeDocumentSidebarTabs from "./sidebar-tabs/ExchangeDocumentSidebarTabs.tsx";

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
    const canViewVersions = usePlanFeature(PlanFeature.DOCUMENT_VERSION_HISTORY).isDiscoverable;
    const styles = useExchangeDocumentSidebarStyles();
    const isMobile = useIsMobile();

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    React.useEffect(() =>
    {
        if ((selectedValue === "versions" && !canViewVersions) || (selectedValue === "audit" && !canViewAudit))
        {
            setSelectedValue("comments");
        }
    }, [canViewAudit, canViewVersions, selectedValue]);

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

                        <ExchangeDocumentSidebarTabs
                            selectedValue={selectedValue}
                            canViewAudit={canViewAudit}
                            canViewVersions={canViewVersions}
                            onTabSelect={onTabSelect}
                        />
                    </div>
                </DrawerHeaderTitle>
            </DrawerHeader>
            <ExchangeDocumentSidebarBody
                selectedValue={selectedValue}
                exchangeDocument={exchangeDocument}
                exchange={exchange}
                canViewAudit={canViewAudit}
                canViewVersions={canViewVersions}
                canUpload={permissions.canUploadDocument}
                canDownload={permissions.canDownloadDocumentsZip}
                pageNumber={pageNumber}
                onNavigateToPage={onNavigateToPage}
            />
        </DrawerComponent>
    );
};

export default ExchangeDocumentSidebar;

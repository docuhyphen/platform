import React from "react";
import {
    Button,
    Caption1,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    InlineDrawer,
    OverlayDrawer,
    SelectTabData,
    SelectTabEvent,
    Tab,
    TabList,
    TabValue,
    Table,
    TableBody,
    TableCell,
    TableRow,
    mergeClasses,
    Text
} from "@fluentui/react-components";
import {ChevronDownRegular, ChevronRightRegular, DismissRegular} from "@fluentui/react-icons";
import {useExchangeDocumentSidebarStyles} from "./ExchangeDocumentSidebarStyles.tsx";
import {DocumentDetailedDto, ExchangeDetailedDto} from "../../../models/models.tsx";
import {AuditIcon, CommentIcon, DocumentVersionsIcon} from "../../../components/IconBundles.tsx";
import ExchangeDocumentComments from "./exchange-document-comments/ExchangeDocumentComments.tsx";
import ExchangeDocumentAudit from "./exchange-document-audit/ExchangeDocumentAudit.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import ExchangeDocumentVersions from "./exchange-document-versions/ExchangeDocumentVersions.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";
import {useIsMobile} from "../../../../utils/useMediaQuery.ts";

type DocumentWithOptionalSize = DocumentDetailedDto & {
    fileSize?: number;
    size?: number;
    sizeBytes?: number;
    contentLength?: number;
};

interface ExchangeDocumentSidebarProps
{
    onOpen: (open: boolean) => void;
    isOpen: boolean;
    exchangeDocument: DocumentDetailedDto;
    exchange: ExchangeDetailedDto;
}

const ExchangeDocumentSidebar: React.FC<ExchangeDocumentSidebarProps> = (
    {
        onOpen,
        isOpen,
        exchangeDocument,
        exchange,
    }) =>
{
    const [selectedValue, setSelectedValue] = React.useState<TabValue>("comments");
    const [showMetadata, setShowMetadata] = React.useState(false);
    const [isMetadataClosing, setIsMetadataClosing] = React.useState(false);
    const {appUser} = useAuth()
    const styles = useExchangeDocumentSidebarStyles();
    const isMobile = useIsMobile();
    const metadataCloseTimeoutRef = React.useRef<number | null>(null);

    React.useEffect(() =>
    {
        return () =>
        {
            if (metadataCloseTimeoutRef.current)
            {
                window.clearTimeout(metadataCloseTimeoutRef.current);
            }
        };
    }, []);

    React.useEffect(() =>
    {
        if (metadataCloseTimeoutRef.current)
        {
            window.clearTimeout(metadataCloseTimeoutRef.current);
        }
        setShowMetadata(false);
        setIsMetadataClosing(false);
    }, [exchangeDocument?.id]);

    const toggleMetadata = () =>
    {
        if (showMetadata)
        {
            if (metadataCloseTimeoutRef.current)
            {
                window.clearTimeout(metadataCloseTimeoutRef.current);
            }
            setIsMetadataClosing(true);
            metadataCloseTimeoutRef.current = window.setTimeout(() =>
            {
                setShowMetadata(false);
                setIsMetadataClosing(false);
            }, 180);
            return;
        }

        if (metadataCloseTimeoutRef.current)
        {
            window.clearTimeout(metadataCloseTimeoutRef.current);
        }
        setIsMetadataClosing(false);
        setShowMetadata(true);
    };

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSelectedValue(data.value);
    };

    const formatDocumentType = (value?: string) =>
    {
        if (!value || value === "null") return "-";
        return value;
    };

    const formatRestrictedType = (value?: string) =>
    {
        if (!value) return "Any supported type";

        const normalized = String(value).trim();
        if (!normalized || normalized.toLowerCase() === "null")
        {
            return "Any supported type";
        }

        return normalized;
    };

    const formatDateValue = (value?: string) =>
    {
        if (!value) return "-";
        return formatDateTimeWithOrdinal(value);
    };

    const formatUploaderName = (firstName?: string, lastName?: string) =>
    {
        const fullName = [firstName, lastName]
            .map((part) => part?.trim())
            .filter((part): part is string => Boolean(part))
            .join(" ");

        return fullName || "-";
    };

    const renderMetadataRow = (label: string, value: string) =>
    {
        return (
            <TableRow className={styles.metadataRow} key={label}>
                <TableCell className={styles.metadataLabelCell}>
                    <Caption1 className={styles.metadataLabel}>{label}</Caption1>
                </TableCell>
                <TableCell className={styles.metadataValueCell}>
                    <Caption1 className={styles.metadataValue}>{value}</Caption1>
                </TableCell>
            </TableRow>
        );
    };

    const getFileSizeLabel = () =>
    {
        const documentWithSize = exchangeDocument as DocumentWithOptionalSize;
        const candidate = documentWithSize.fileSize ??
            documentWithSize.size ??
            documentWithSize.sizeBytes ??
            documentWithSize.contentLength;

        if (candidate === undefined || candidate === null || Number.isNaN(Number(candidate)))
        {
            return "Not available";
        }

        const bytes = Number(candidate);
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
        return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
    };

    const metadataRows = [
        {label: "Date Added", value: formatDateValue(exchangeDocument?.createdDate)},
        {label: "Latest Uploaded Date", value: formatDateValue(exchangeDocument?.uploadDate)},
        {
            label: "Last Uploaded By",
            value: formatUploaderName(exchangeDocument?.lastUploadedByFirstName, exchangeDocument?.lastUploadedByLastName)
        },
        {label: "Type", value: formatDocumentType(exchangeDocument?.type)},
        {label: "Restricted type", value: formatRestrictedType(exchangeDocument?.restrictedType)},
        {label: "File size", value: getFileSizeLabel()},
    ];

    const isMetadataExpanded = showMetadata && !isMetadataClosing;
    const shouldRenderMetadataPanel = showMetadata || isMetadataClosing;

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
            as="aside"
            id={"ExchangeDocumentSidebar"}
            open={isOpen}
            className={mergeClasses(styles.sidebarContainer, isMobile && styles.sidebarContainerMobile)}
            position="end"
            {...drawerProps}
        >
            <DrawerHeader className={styles.drawerHeader}>
                <DrawerHeaderTitle
                    className={styles.drawerHeaderTitle}
                    action={
                        <Button
                            size={"small"}
                            appearance="subtle"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                            onClick={() => onOpen(false)}
                        />
                    }>
                    <div className={styles.headerContent}>
                        <div className={styles.documentTitleRow}>
                            <Button
                                id="exchange-document-sidebar-metadata-toggle"
                                className={styles.metadataToggleButton}
                                appearance="subtle"
                                size="small"
                                shape="circular"
                                aria-label={isMetadataExpanded ? "Hide document details" : "Show document details"}
                                icon={isMetadataExpanded ? <ChevronDownRegular/> : <ChevronRightRegular/>}
                                onClick={toggleMetadata}
                            />
                            <Text className={styles.documentTitle}>{exchangeDocument.title}</Text>
                        </div>

                        {shouldRenderMetadataPanel && (
                            <section
                                id="exchange-document-sidebar-metadata-panel"
                                className={mergeClasses(
                                    styles.metadataPanel,
                                    isMetadataExpanded ? styles.metadataPanelEntering : styles.metadataPanelLeaving,
                                )}>
                                <Table noNativeElements size="small" className={styles.metadataTable}>
                                    <TableBody>
                                        {metadataRows.map((row) => renderMetadataRow(row.label, row.value))}
                                    </TableBody>
                                </Table>
                            </section>
                        )}

                        <TabList selectedValue={selectedValue} onTabSelect={onTabSelect}>
                            <Tab id="comments" icon={<CommentIcon/>} value="comments">
                                Notes
                            </Tab>
                            <Tab id="versions" icon={<DocumentVersionsIcon/>} value="versions">
                                Versions
                            </Tab>
                            <Tab id="audit" icon={<AuditIcon/>} value="audit">
                                Audit
                            </Tab>
                        </TabList>
                    </div>
                </DrawerHeaderTitle>
            </DrawerHeader>
            <DrawerBody className={styles.drawerBody}>
                {exchange && selectedValue === "comments" && (
                    <ExchangeDocumentComments
                        exchangeId={exchange.id}
                        exchangeDocument={exchangeDocument}
                        currentUserEmail={appUser?.email}
                    />
                )}
                {selectedValue === "versions" && (
                    <ExchangeDocumentVersions
                        exchangeId={exchange.id}
                        exchangeDocument={exchangeDocument}
                        exchange={exchange}
                    />
                )}
                {selectedValue === "audit" && (
                    <ExchangeDocumentAudit
                        exchangeId={exchange.id}
                        exchangeDocument={exchangeDocument}
                    />
                )}
            </DrawerBody>
        </DrawerComponent>
    );
};

export default ExchangeDocumentSidebar;
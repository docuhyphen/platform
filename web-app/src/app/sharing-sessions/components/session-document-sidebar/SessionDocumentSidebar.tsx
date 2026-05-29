import React from "react";
import {
    Button,
    Caption1,
    DrawerBody,
    DrawerHeader,
    DrawerHeaderTitle,
    InlineDrawer,
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
import {useSessionDocumentSidebarStyles} from "./SessionDocumentSidebarStyles.tsx";
import {DocumentDetailedDto, SharingSessionDetailedDto} from "../../../models/models.tsx";
import {AuditIcon, CommentIcon, DocumentVersionsIcon} from "../../../components/IconBundles.tsx";
import SessionDocumentComments from "./session-document-comments/SessionDocumentComments.tsx";
import SessionDocumentAudit from "./session-document-audit/SessionDocumentAudit.tsx";
import {useAuth} from "../../../../context/AuthContext.tsx";
import SessionDocumentVersions from "./session-document-versions/SessionDocumentVersions.tsx";
import {formatDateTimeWithOrdinal} from "../../../helpers.ts";

type DocumentWithOptionalSize = DocumentDetailedDto & {
    fileSize?: number;
    size?: number;
    sizeBytes?: number;
    contentLength?: number;
};

interface SessionDocumentSidebarProps
{
    onOpen: (open: boolean) => void;
    isOpen: boolean;
    sessionDocument: DocumentDetailedDto;
    session: SharingSessionDetailedDto;
}

const SessionDocumentSidebar: React.FC<SessionDocumentSidebarProps> = (
    {
        onOpen,
        isOpen,
        sessionDocument,
        session,
    }) =>
{
    const [selectedValue, setSelectedValue] = React.useState<TabValue>("comments");
    const [showMetadata, setShowMetadata] = React.useState(false);
    const [isMetadataClosing, setIsMetadataClosing] = React.useState(false);
    const {appUser} = useAuth()
    const styles = useSessionDocumentSidebarStyles();
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
    }, [sessionDocument?.id]);

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
        if (!value || value === "null") return "Any supported type";
        return value;
    };

    const formatDateValue = (value?: string) =>
    {
        if (!value) return "-";
        return formatDateTimeWithOrdinal(value);
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
        const documentWithSize = sessionDocument as DocumentWithOptionalSize;
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
        {label: "Created", value: formatDateValue(sessionDocument?.createdDate)},
        {label: "Last Uploaded", value: formatDateValue(sessionDocument?.uploadDate)},
        {label: "Type", value: formatDocumentType(sessionDocument?.type)},
        {label: "Restricted type", value: formatRestrictedType(sessionDocument?.restrictedType)},
        {label: "File size", value: getFileSizeLabel()},
    ];

    const isMetadataExpanded = showMetadata && !isMetadataClosing;
    const shouldRenderMetadataPanel = showMetadata || isMetadataClosing;

    return (
        <InlineDrawer
            as="aside"
            id={"SessionDocumentSidebar"}
            open={isOpen}
            className={styles.sidebarContainer}
            position="end"
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
                                id="session-document-sidebar-metadata-toggle"
                                className={styles.metadataToggleButton}
                                appearance="subtle"
                                size="small"
                                shape="circular"
                                aria-label={isMetadataExpanded ? "Hide document details" : "Show document details"}
                                icon={isMetadataExpanded ? <ChevronDownRegular/> : <ChevronRightRegular/>}
                                onClick={toggleMetadata}
                            />
                            <Text className={styles.documentTitle}>{sessionDocument.title}</Text>
                        </div>

                        {shouldRenderMetadataPanel && (
                            <section
                                id="session-document-sidebar-metadata-panel"
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
                {session && selectedValue === "comments" && (
                    <SessionDocumentComments
                        sessionId={session.id}
                        sessionDocument={sessionDocument}
                        currentUserEmail={appUser?.email}
                    />
                )}
                {selectedValue === "versions" && (
                    <SessionDocumentVersions
                        sessionId={session.id}
                        sessionDocument={sessionDocument}
                    />
                )}
                {selectedValue === "audit" && (
                    <SessionDocumentAudit
                        sessionId={session.id}
                        sessionDocument={sessionDocument}
                    />
                )}
            </DrawerBody>
        </InlineDrawer>
    );
};

export default SessionDocumentSidebar;
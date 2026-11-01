import React from "react";
import {
    Badge,
    Button,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    Spinner,
    Text,
} from "@fluentui/react-components";
import {ArrowDownloadRegular, EyeRegular, MoreVerticalRegular} from "@fluentui/react-icons";
import {DocumentVersion} from "../../../../../models/models";
import {formatDate} from "../../../../../helpers.ts";
import {useExchangeDocumentVersionCardStyles} from "./ExchangeDocumentVersionCardStyles.tsx";

interface ExchangeDocumentVersionCardProps
{
    version: DocumentVersion;
    canDownload: boolean;
    actionInProgress: boolean;
    onPreview: (versionId: string) => void;
    onDownload: (versionId: string) => void;
}

const ExchangeDocumentVersionCard: React.FC<ExchangeDocumentVersionCardProps> = (
    {
        version,
        canDownload,
        actionInProgress,
        onPreview,
        onDownload,
    }) =>
{
    const styles = useExchangeDocumentVersionCardStyles();

    return (
        <div id={`exchange-document-versions-div-4-${version.id}`}
             className={styles.versionCard}>
            <div id={"exchange-document-versions-div-5"}
                 className={styles.versionCardHeader}>
                <Badge id={"exchange-document-versions-badge-1"}
                       appearance="filled"
                       color="informative">
                    v{version.version}
                </Badge>
                {canDownload && (
                    <Menu>
                        <MenuTrigger disableButtonEnhancement>
                            <Button id={`version-actions-trigger-${version.id}`}
                                    icon={actionInProgress
                                        ? <Spinner id={"exchange-document-versions-spinner-2"}
                                                   size="tiny"/>
                                        : <MoreVerticalRegular/>}
                                    appearance="subtle"
                                    shape={"circular"}
                                    size="small"
                                    disabled={actionInProgress}/>
                        </MenuTrigger>
                        <MenuPopover id={"exchange-document-versions-menu-popover-1"}>
                            <MenuList id={"exchange-document-versions-menu-list-1"}>
                                <MenuItem id={"exchange-document-versions-menu-item-1"}
                                          icon={<EyeRegular/>}
                                          onClick={() => onPreview(version.id)}>
                                    Preview
                                </MenuItem>
                                <MenuItem id={"exchange-document-versions-menu-item-2"}
                                          icon={<ArrowDownloadRegular/>}
                                          onClick={() => onDownload(version.id)}>
                                    Download
                                </MenuItem>
                            </MenuList>
                        </MenuPopover>
                    </Menu>
                )}
            </div>
            <div id={"exchange-document-versions-div-6"}
                 className={styles.versionCardMeta}>
                <Text id={"exchange-document-versions-text-4"}
                      size={200}>{formatDate(version.createdAt)}</Text>
                <Text id={"exchange-document-versions-text-5"}
                      size={200}>{version.createdByEmail || "Unknown"}</Text>
            </div>
            {version.fileName && (
                <Text id={"exchange-document-versions-text-6"}
                      size={200}
                      className={styles.versionFileName}>
                    {version.fileName}
                </Text>
            )}
        </div>
    );
};

export default ExchangeDocumentVersionCard;

import {Button, Divider, mergeClasses, Text, Tooltip} from "@fluentui/react-components";
import {CollapseIcon, FullScreenEnterIcon, FullScreenExitIcon} from "../../../../components/IconBundles.tsx";
import {PreviewPageNavigation} from "../usePreviewPageNavigation.ts";
import {PreviewZoom} from "../usePreviewZoom.ts";
import {PreviewEnlargement} from "../usePreviewEnlargement.ts";
import PreviewPageControls from "../preview-page-controls/PreviewPageControls.tsx";
import PreviewZoomControls from "../preview-zoom-controls/PreviewZoomControls.tsx";
import PreviewNotesToggle from "../preview-notes-toggle/PreviewNotesToggle.tsx";
import {useEnlargedPreviewHeaderStyles} from "./EnlargedPreviewHeaderStyles.tsx";

interface EnlargedPreviewHeaderProps
{
    exchangeName: string;
    documentTitle: string;
    isMobile: boolean;
    unreadCommentCount: number;
    navigation: PreviewPageNavigation;
    zoom: PreviewZoom;
    enlargement: PreviewEnlargement;
}

const EnlargedPreviewHeader = (
    {
        exchangeName,
        documentTitle,
        isMobile,
        unreadCommentCount,
        navigation,
        zoom,
        enlargement,
    }: EnlargedPreviewHeaderProps) =>
{
    const styles = useEnlargedPreviewHeaderStyles();
    const {isFullscreen, isClosingEnlarged, isNotesPanelOpen} = enlargement;

    return (
        <div id={"exchange-document-previewer-div-13"}
             className={mergeClasses(styles.enlargedPreviewHeader, isClosingEnlarged && styles.enlargedPreviewHeaderClosing)}>
            <div id={"exchange-document-previewer-div-14"}
                 className={styles.documentName}>
                <Text id={"exchange-document-previewer-text-12"}
                      size={200}>{exchangeName}</Text>
                <Text id={"exchange-document-previewer-text-13"}
                      size={500}>{documentTitle}</Text>
            </div>
            <div id={"exchange-document-previewer-div-15"}
                 className={styles.enlargedPreviewHeaderActions}>
                <PreviewPageControls containerId={"exchange-document-previewer-div-16"}
                                     pageCountTextId={"exchange-document-previewer-text-14"}
                                     navigation={navigation}/>

                {!isMobile && (
                    <>
                        <PreviewZoomControls variant={"enlarged"}
                                             leadingDividerId={"exchange-document-previewer-divider-3"}
                                             trailingDividerId={"exchange-document-previewer-divider-4"}
                                             zoom={zoom}/>

                        <Tooltip content={isFullscreen ? "Exit fullscreen" : "Fullscreen"}
                                 relationship="description">
                            <Button onClick={enlargement.toggleFullscreen}
                                    id="exchange-document-preview-fullscreen-enlarged"
                                    appearance="transparent"
                                    shape={"circular"}
                                    icon={isFullscreen ? <FullScreenExitIcon/> : <FullScreenEnterIcon/>}/>
                        </Tooltip>
                    </>
                )}

                <Divider id={"exchange-document-previewer-divider-5"}
                         vertical
                         className={styles.dividerFullHeight}/>

                <PreviewNotesToggle isNotesPanelOpen={isNotesPanelOpen}
                                    unreadCommentCount={unreadCommentCount}
                                    onToggle={enlargement.toggleNotesPanel}/>

                <Divider id={"exchange-document-previewer-divider-6"}
                         vertical
                         className={styles.dividerFullHeight}/>

                <Tooltip content="Exit"
                         relationship="description">
                    <Button onClick={enlargement.toggleEnlarge}
                            id="exchange-document-preview-exit-enlarged"
                            appearance="transparent"
                            shape={"circular"}
                            icon={<CollapseIcon/>}/>
                </Tooltip>
            </div>
        </div>
    );
};

export default EnlargedPreviewHeader;

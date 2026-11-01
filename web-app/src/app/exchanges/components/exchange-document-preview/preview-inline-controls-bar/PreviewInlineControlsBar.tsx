import {Button, Tooltip} from "@fluentui/react-components";
import {ExpandIcon, FullScreenEnterIcon} from "../../../../components/IconBundles.tsx";
import {PreviewPageNavigation} from "../usePreviewPageNavigation.ts";
import {PreviewZoom} from "../usePreviewZoom.ts";
import {COMPACT_CONTROLS_WIDTH_THRESHOLD} from "../previewPageVirtualization.ts";
import PreviewPageControls from "../preview-page-controls/PreviewPageControls.tsx";
import PreviewZoomControls from "../preview-zoom-controls/PreviewZoomControls.tsx";
import {usePreviewInlineControlsBarStyles} from "./PreviewInlineControlsBarStyles.tsx";

interface PreviewInlineControlsBarProps
{
    navigation: PreviewPageNavigation;
    zoom: PreviewZoom;
    isMobile: boolean;
    containerWidth: number;
    hideEnlarge: boolean;
    onToggleFullscreen: () => void;
    onToggleEnlarge: () => void;
}

const PreviewInlineControlsBar = (
    {
        navigation,
        zoom,
        isMobile,
        containerWidth,
        hideEnlarge,
        onToggleFullscreen,
        onToggleEnlarge,
    }: PreviewInlineControlsBarProps) =>
{
    const styles = usePreviewInlineControlsBarStyles();
    const isCompactControls = containerWidth > 0 && containerWidth < COMPACT_CONTROLS_WIDTH_THRESHOLD;
    const showZoomAndFullscreen = !isMobile && !isCompactControls;

    return (
        <div className={styles.inlineControlsBar}
             id="exchange-document-preview-inline-controls">
            <PreviewPageControls containerId={"exchange-document-previewer-div-12"}
                                 pageCountTextId={"exchange-document-previewer-text-11"}
                                 navigation={navigation}/>

            {showZoomAndFullscreen && (
                <PreviewZoomControls variant={"inline"}
                                     leadingDividerId={"exchange-document-previewer-divider-1"}
                                     trailingDividerId={"exchange-document-previewer-divider-2"}
                                     zoom={zoom}/>
            )}

            {showZoomAndFullscreen && (
                <Tooltip content="Fullscreen"
                         relationship="description">
                    <Button onClick={onToggleFullscreen}
                            id="exchange-document-preview-fullscreen-inline"
                            appearance="transparent"
                            shape={"circular"}
                            icon={<FullScreenEnterIcon/>}/>
                </Tooltip>
            )}

            <Tooltip content="Enlarge"
                     relationship="description">
                <Button onClick={onToggleEnlarge}
                        id="exchange-document-preview-expand"
                        appearance="transparent"
                        shape={"circular"}
                        className={hideEnlarge ? styles.hiddenControl : undefined}
                        icon={<ExpandIcon/>}/>
            </Tooltip>
        </div>
    );
};

export default PreviewInlineControlsBar;

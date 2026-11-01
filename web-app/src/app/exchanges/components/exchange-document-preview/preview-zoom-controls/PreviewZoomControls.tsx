import {Button, Divider, Tooltip} from "@fluentui/react-components";
import {ResetZoomIcon, ZoomInIcon, ZoomOutIcon} from "../../../../components/IconBundles.tsx";
import {PreviewZoom} from "../usePreviewZoom.ts";
import {usePreviewZoomControlsStyles} from "./PreviewZoomControlsStyles.tsx";

interface PreviewZoomControlsProps
{
    variant: "inline" | "enlarged";
    leadingDividerId: string;
    trailingDividerId: string;
    zoom: PreviewZoom;
}

const PreviewZoomControls = ({variant, leadingDividerId, trailingDividerId, zoom}: PreviewZoomControlsProps) =>
{
    const styles = usePreviewZoomControlsStyles();

    return (
        <>
            <Divider id={leadingDividerId}
                     vertical
                     className={styles.dividerFullHeight}/>

            <Button onClick={zoom.zoomOut}
                    id={`exchange-document-preview-zoom-out-${variant}`}
                    appearance="transparent"
                    shape={"circular"}
                    icon={<ZoomOutIcon/>}/>

            <Tooltip content="Click to reset"
                     relationship="description">
                <Button onClick={zoom.resetZoom}
                        id={`exchange-document-preview-zoom-reset-${variant}`}
                        icon={<ResetZoomIcon/>}
                        shape={"circular"}
                        appearance="secondary">
                    {Math.round(zoom.scale * 100)}%
                </Button>
            </Tooltip>

            <Button onClick={zoom.zoomIn}
                    id={`exchange-document-preview-zoom-in-${variant}`}
                    appearance="transparent"
                    shape={"circular"}
                    icon={<ZoomInIcon/>}/>

            <Divider id={trailingDividerId}
                     vertical
                     className={styles.dividerFullHeight}/>
        </>
    );
};

export default PreviewZoomControls;

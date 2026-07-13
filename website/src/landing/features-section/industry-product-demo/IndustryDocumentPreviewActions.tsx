import {
    Button,
    Divider,
    Tooltip,
} from "@fluentui/react-components";
import {
    ArrowExpandFilled,
    ArrowExpandRegular,
    FullScreenMaximizeFilled,
    FullScreenMaximizeRegular,
    ZoomFitFilled,
    ZoomFitRegular,
    ZoomInFilled,
    ZoomInRegular,
    ZoomOutFilled,
    ZoomOutRegular,
    bundleIcon,
} from "@fluentui/react-icons";
import {IndustryDocumentPageControls} from "./IndustryDocumentPageControls.tsx";
import {useIndustryDocumentToolbarStyles} from "./IndustryDocumentToolbarStyles.tsx";

const ExpandIcon = bundleIcon(ArrowExpandFilled, ArrowExpandRegular);
const FullScreenEnterIcon = bundleIcon(FullScreenMaximizeFilled, FullScreenMaximizeRegular);
const ZoomInIcon = bundleIcon(ZoomInFilled, ZoomInRegular);
const ZoomOutIcon = bundleIcon(ZoomOutFilled, ZoomOutRegular);
const ResetZoomIcon = bundleIcon(ZoomFitFilled, ZoomFitRegular);

export function IndustryDocumentPreviewActions()
{
    const styles = useIndustryDocumentToolbarStyles();

    return (
        <div
            id="industry-demo-preview-header-actions"
            className={styles.previewHeaderActions}
        >
            <Tooltip
                content="Enlarge"
                relationship="description"
            >
                <Button
                    id="industry-demo-document-preview-expand"
                    appearance="transparent"
                    shape="circular"
                    aria-label="Enlarge"
                    icon={<ExpandIcon id="industry-demo-document-preview-expand-icon"/>}
                />
            </Tooltip>

            <Tooltip
                content="Fullscreen"
                relationship="description"
            >
                <Button
                    id="industry-demo-document-preview-fullscreen-inline"
                    appearance="transparent"
                    shape="circular"
                    aria-label="Fullscreen"
                    icon={<FullScreenEnterIcon id="industry-demo-document-preview-fullscreen-icon"/>}
                />
            </Tooltip>

            <Divider
                id="industry-demo-document-preview-zoom-divider-after"
                vertical
                className={styles.dividerFullHeight}
            />

            <Button
                id="industry-demo-document-preview-zoom-in-inline"
                appearance="transparent"
                shape="circular"
                aria-label="Zoom in"
                icon={<ZoomInIcon id="industry-demo-document-preview-zoom-in-icon"/>}
            />

            <Tooltip
                content="Click to reset"
                relationship="description"
            >
                <Button
                    id="industry-demo-document-preview-zoom-reset-inline"
                    appearance="secondary"
                    shape="circular"
                    icon={<ResetZoomIcon id="industry-demo-document-preview-zoom-reset-icon"/>}
                >
                    180%
                </Button>
            </Tooltip>

            <Button
                id="industry-demo-document-preview-zoom-out-inline"
                appearance="transparent"
                shape="circular"
                aria-label="Zoom out"
                icon={<ZoomOutIcon id="industry-demo-document-preview-zoom-out-icon"/>}
            />

            <Divider
                id="industry-demo-document-preview-page-divider"
                vertical
                className={styles.dividerFullHeight}
            />

            <IndustryDocumentPageControls/>
        </div>
    );
}

import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewScrollPaneStyles = makeStyles({
    pdfDocumentContainer: {
        justifyContent: "center",
        display: "flex",
        flex: "1",
        background: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        overflow: "auto",
        maxWidth: "100%",
        margin: "0 auto",
        minWidth: "480px",
        minHeight: 0,
        padding: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        width: "100%",
        "@media (max-width: 768px)": {
            minWidth: 0,
            padding: tokens.spacingHorizontalS,
            touchAction: "pan-x pan-y pinch-zoom",
        },
    },

    mainDocumentPane: {
        display: "flex",
        justifyContent: "center",
        flex: 1,
        minWidth: 0,
        minHeight: 0,
        overflow: "auto",
        padding: tokens.spacingHorizontalL,
        boxSizing: "border-box",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        "@media (max-width: 768px)": {
            padding: 0,
            touchAction: "pan-x pan-y pinch-zoom",
        },
    },
});

import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationPreviewTabStyles = makeStyles({
    tabContent: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },
    renderPreviewButton: {
        alignSelf: "flex-start",
    },
    previewErrorText: {
        color: tokens.colorPaletteRedForeground1,
    },
});

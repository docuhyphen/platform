import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewLoadingIndicatorStyles = makeStyles({
    pdfLoadingContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalMNudge,
        minHeight: "240px",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        minWidth: "100%",
    },

    pdfLoadingText: {
        color: tokens.colorNeutralForeground2,
    },
});

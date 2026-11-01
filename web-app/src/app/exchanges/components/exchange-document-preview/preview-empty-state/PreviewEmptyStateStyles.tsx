import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewEmptyStateStyles = makeStyles({
    previewEmptyState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalMNudge,
        minHeight: "280px",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusLarge,
        backgroundColor: tokens.colorNeutralBackground1,
        padding: tokens.spacingHorizontalXL,
        textAlign: "center",
        margin: "auto",
        maxWidth: "380px"
    },

    previewEmptySubText: {
        color: tokens.colorNeutralForeground2,
    },
});

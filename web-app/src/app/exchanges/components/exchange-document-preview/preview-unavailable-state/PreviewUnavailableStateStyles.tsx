import {makeStyles, tokens} from '@fluentui/react-components';

export const usePreviewUnavailableStateStyles = makeStyles({
    previewErrorContainer: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: tokens.spacingHorizontalM,
        padding: tokens.spacingHorizontalXXXL,
        textAlign: "center",
    },
});

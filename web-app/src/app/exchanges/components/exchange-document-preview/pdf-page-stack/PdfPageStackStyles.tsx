import {makeStyles, tokens} from '@fluentui/react-components';

export const usePdfPageStackStyles = makeStyles({
    pdfRelativeWrapper: {
        position: "relative",
    },

    pdfDocument: {},

    pdfPagesStack: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalL,
        alignItems: "center",
    },
});

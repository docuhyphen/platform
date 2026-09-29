import {makeStyles, tokens} from "@fluentui/react-components";

export const useEvidenceKeptUploadStyles = makeStyles({
    kept: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        paddingTop: tokens.spacingVerticalXS,
        paddingBottom: tokens.spacingVerticalXS,
    },
    text: {
        flexGrow: 1,
        minWidth: 0,
        overflowWrap: "anywhere",
    },
});

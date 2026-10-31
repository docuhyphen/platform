import {makeStyles, tokens} from "@fluentui/react-components";

export const useCreatedInformationRequestSummaryStyles = makeStyles({
    summary: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: tokens.spacingVerticalXS,
        width: "100%",
        maxWidth: "420px",
        textAlign: "center",
    },
    label: {
        color: tokens.colorNeutralForeground3,
    },
    title: {
        overflowWrap: "anywhere",
    },
    unnamed: {
        maxWidth: "420px",
        textAlign: "center",
    },
});

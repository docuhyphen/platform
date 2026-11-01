import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventDetailMoreInfoStyles = makeStyles({
    fieldGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        gap: tokens.spacingVerticalS,
    },
    payloadList: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    payloadRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        wordBreak: "break-all",
    },
    note: {
        color: tokens.colorNeutralForeground3,
    },
});

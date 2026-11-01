import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditEventDetailFieldStyles = makeStyles({
    field: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXS,
        minWidth: 0,
    },
});

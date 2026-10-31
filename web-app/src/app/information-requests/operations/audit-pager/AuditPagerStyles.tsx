import {makeStyles, tokens} from "@fluentui/react-components";

export const useAuditPagerStyles = makeStyles({
    pager: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalM,
        flexWrap: "wrap",
    },
});

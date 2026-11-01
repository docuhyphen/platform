import {makeStyles, tokens} from "@fluentui/react-components";

export const useApprovalSlaFieldsStyles = makeStyles({
    field: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },
});

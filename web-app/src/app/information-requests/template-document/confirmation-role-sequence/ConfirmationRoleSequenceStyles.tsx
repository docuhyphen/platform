import {makeStyles, tokens} from "@fluentui/react-components";

export const useConfirmationRoleSequenceStyles = makeStyles({
    sequence: {
        display: "flex",
        flexDirection: "column",
        rowGap: tokens.spacingVerticalXS,
        margin: 0,
        paddingLeft: tokens.spacingHorizontalXL,
    },
    step: {
        display: "flex",
        alignItems: "center",
        columnGap: tokens.spacingHorizontalS,
    },
});

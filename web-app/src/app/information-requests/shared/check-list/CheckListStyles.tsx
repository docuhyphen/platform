import {makeStyles, tokens} from "@fluentui/react-components";

export const useCheckListStyles = makeStyles({
    options: {
        display: "flex",
        flexWrap: "wrap",
        columnGap: tokens.spacingHorizontalL,
        rowGap: tokens.spacingVerticalXS,
    },
});

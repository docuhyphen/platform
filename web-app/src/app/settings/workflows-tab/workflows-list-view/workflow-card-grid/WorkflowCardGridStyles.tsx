import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowCardGridStyles = makeStyles({
    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },
});

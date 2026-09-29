import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementQuestionFieldsStyles = makeStyles({
    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 620px)": {
            gridTemplateColumns: "1fr",
        },
    },
    wide: {
        gridColumn: "1 / -1",
    },
});

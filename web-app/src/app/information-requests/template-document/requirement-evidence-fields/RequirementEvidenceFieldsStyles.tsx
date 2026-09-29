import {makeStyles, tokens} from "@fluentui/react-components";

export const useRequirementEvidenceFieldsStyles = makeStyles({
    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 820px)": {
            gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
        },
        "@media screen and (max-width: 520px)": {
            gridTemplateColumns: "1fr",
        },
    },
    wide: {
        gridColumn: "1 / -1",
    },
});

import {makeStyles, tokens} from "@fluentui/react-components";

export const useEvidenceDetailsDialogStyles = makeStyles({
    surface: {
        maxWidth: `min(560px, calc(100vw - (${tokens.spacingHorizontalXXL} * 2)))`,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
    },
    fields: {
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(min(220px, 100%), 1fr))",
        rowGap: tokens.spacingVerticalM,
        columnGap: tokens.spacingHorizontalM,
    },
    problem: {
        color: tokens.colorPaletteRedForeground1,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});

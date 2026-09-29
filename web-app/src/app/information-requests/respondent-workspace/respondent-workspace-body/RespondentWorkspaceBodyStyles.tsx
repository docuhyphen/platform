import {makeStyles, tokens} from "@fluentui/react-components";

export const useRespondentWorkspaceBodyStyles = makeStyles({
    body: {
        display: "grid",
        gridTemplateColumns: "minmax(180px, 240px) minmax(0, 1fr)",
        gap: tokens.spacingHorizontalXXL,
        alignItems: "start",
        "@media (max-width: 900px)": {
            gridTemplateColumns: "minmax(0, 1fr)",
            rowGap: tokens.spacingVerticalL,
        },
    },
    main: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXL,
        minWidth: 0,
    },
});

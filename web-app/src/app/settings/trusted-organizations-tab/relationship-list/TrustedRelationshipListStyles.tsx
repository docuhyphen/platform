import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useTrustedRelationshipListStyles = makeStyles({
    root: {
        display: "grid",
        gridTemplateRows: "auto 1fr",
        gap: tokens.spacingVerticalS,
        minHeight: 0,
    },
    list: {
        display: "grid",
        alignContent: "start",
        gap: tokens.spacingVerticalS,
        overflowY: "auto",
    },
    item: {
        justifyContent: "flex-start",
        minHeight: "4.5rem",
        ...shorthands.padding(tokens.spacingVerticalS, tokens.spacingHorizontalM),
    },
    itemContent: {
        display: "grid",
        justifyItems: "start",
        gap: tokens.spacingVerticalXXS,
        width: "100%",
        textAlign: "left",
    },
    selected: {
        backgroundColor: tokens.colorNeutralBackground1Selected,
    },
    meta: {
        color: tokens.colorNeutralForeground2,
    },
});

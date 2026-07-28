import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

const APP_HEADER_HEIGHT = "3.75rem";

export const usePlatformAuditStyles = makeStyles({
    container: {
        height: "100%",
        display: "flex",
        flexDirection: "column",
        boxSizing: "border-box",
        overflow: "hidden",
        backgroundColor: tokens.colorNeutralBackground1,
        paddingTop: APP_HEADER_HEIGHT,
    },
    workspace: {
        flex: 1,
        width: "100%",
        maxWidth: "85rem",
        minHeight: 0,
        marginLeft: "auto",
        marginRight: "auto",
        boxSizing: "border-box",
        ...shorthands.padding(
            tokens.spacingVerticalL,
            tokens.spacingHorizontalXXXL,
            `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL})`,
        ),
        "@media (max-width: 1024px)": {
            ...shorthands.padding(
                tokens.spacingVerticalL,
                tokens.spacingHorizontalXXL,
                `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL})`,
            ),
        },
        "@media (max-width: 768px)": {
            ...shorthands.padding(
                tokens.spacingVerticalS,
                tokens.spacingHorizontalM,
                `calc(${tokens.spacingVerticalXXXL} + ${tokens.spacingVerticalL})`,
            ),
        },
    },
});

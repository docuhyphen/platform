import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentVersionCardStyles = makeStyles({
    versionCard: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        ...shorthands.padding(tokens.spacingVerticalS, tokens.spacingHorizontalM),
        ...shorthands.border("1px", "solid", tokens.colorNeutralStroke1),
        ...shorthands.borderRadius(tokens.borderRadiusMedium),
        backgroundColor: tokens.colorNeutralBackground1,
    },
    versionCardHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
    },
    versionCardMeta: {
        display: "flex",
        gap: tokens.spacingHorizontalM,
        color: tokens.colorNeutralForeground3,
    },
    versionFileName: {
        color: tokens.colorNeutralForeground3,
        ...shorthands.overflow("hidden"),
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
});

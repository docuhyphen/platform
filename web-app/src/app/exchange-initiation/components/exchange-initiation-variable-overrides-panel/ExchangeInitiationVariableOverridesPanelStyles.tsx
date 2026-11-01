import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeInitiationVariableOverridesPanelStyles = makeStyles({
    variableOverridesPanel: {
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        border: `1px solid ${tokens.colorBrandStroke2}`,
        borderRadius: tokens.borderRadiusXLarge,
        background: tokens.colorNeutralBackground2,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        marginBottom: tokens.spacingVerticalM,
    },
    variableOverridesSubtext: {
        color: tokens.colorNeutralForeground3,
    },
    variableOverridesRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
    },
    variableOverridesLabel: {
        minWidth: "120px",
        fontFamily: "monospace",
    },
    variableOverridesInput: {
        flex: 1,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
        borderRadius: tokens.borderRadiusMedium,
        border: `1px solid ${tokens.colorNeutralStroke1}`,
        background: tokens.colorNeutralBackground1,
        color: "inherit",
    },
});

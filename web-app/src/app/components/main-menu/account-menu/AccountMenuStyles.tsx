import {makeStyles, tokens} from "@fluentui/react-components";

export const useAccountMenuStyles = makeStyles({
    tourAnchor: {
        display: "inline-flex",
        alignItems: "center",
    },
    persona: {
        minWidth: 0,
        paddingLeft: tokens.spacingHorizontalXS,
        paddingRight: tokens.spacingHorizontalXS,
        "@media (max-width: 768px)": {
            paddingLeft: tokens.spacingHorizontalXXS,
            paddingRight: tokens.spacingHorizontalXXS,
            "& .fui-Persona__primaryText, & .fui-Persona__secondaryText, & .fui-Persona__tertiaryText, & .fui-Persona__quaternaryText": {
                display: "none",
            },
            "& .fui-Persona": {
                gap: 0,
            },
        },
    },
});

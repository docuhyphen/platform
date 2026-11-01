import {makeStyles, tokens} from '@fluentui/react-components';

export const useSequenceDrawerStyles = makeStyles({
    drawerBody: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalL,
        paddingTop: tokens.spacingVerticalL,
    },
    previewBox: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        background: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusLarge,
    },
    previewLabel: {
        color: tokens.colorNeutralForeground3,
    },
    formError: {
        color: tokens.colorPaletteRedForeground1,
    },
    buttonRow: {
        display: 'flex',
        gap: tokens.spacingHorizontalS,
        justifyContent: 'flex-end',
    },
});

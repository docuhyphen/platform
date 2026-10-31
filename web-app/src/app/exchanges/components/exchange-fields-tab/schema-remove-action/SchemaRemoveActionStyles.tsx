import {makeStyles, tokens} from '@fluentui/react-components';

export const useSchemaRemoveActionStyles = makeStyles({
    surface: {
        maxWidth: `min(480px, calc(100vw - (${tokens.spacingHorizontalXXL} * 2)))`,
    },
    content: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingVerticalM,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
});

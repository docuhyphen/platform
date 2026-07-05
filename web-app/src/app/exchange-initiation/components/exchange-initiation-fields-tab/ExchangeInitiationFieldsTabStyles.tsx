import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeInitiationFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingVerticalS,
    },
    schemaRow: {
        display: 'flex',
        gap: '8px',
    },
    fieldList: {
        display: 'flex',
        flexDirection: 'column',
        gap: '14px',
    },
    subText: {
        color: tokens.colorNeutralForeground3,
    },
    errorText: {
        color: tokens.colorPaletteRedForeground1,
    },
});

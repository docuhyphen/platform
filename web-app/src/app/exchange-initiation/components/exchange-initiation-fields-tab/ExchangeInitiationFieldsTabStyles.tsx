import {makeStyles, tokens} from '@fluentui/react-components';

export const useExchangeInitiationFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        gap: '16px',
        padding: '4px',
        width: '100%',
    },
    schemaRow: {
        display: 'flex',
        gap: '8px',
        alignItems: 'flex-end',
        flexWrap: 'wrap',
        border: "2px solid red",
        boxSizing: "border-box"
    },
    grow: {
        flex: '1',
        minWidth: '220px',
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

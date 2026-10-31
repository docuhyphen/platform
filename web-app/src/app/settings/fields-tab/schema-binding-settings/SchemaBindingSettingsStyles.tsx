import {makeStyles, tokens} from '@fluentui/react-components';

export const useSchemaBindingSettingsStyles = makeStyles({
    settings: {
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingVerticalS,
    },
    placement: {
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 200px), 1fr))',
        gap: tokens.spacingHorizontalM,
    },
    defaultRow: {
        display: 'flex',
        alignItems: 'flex-end',
        gap: tokens.spacingHorizontalS,
        flexWrap: 'wrap',
    },
    defaultEditor: {
        flex: '1 1 240px',
        minWidth: 0,
    },
});

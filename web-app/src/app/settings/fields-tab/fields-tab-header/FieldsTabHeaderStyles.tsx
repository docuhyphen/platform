import {makeStyles, tokens} from '@fluentui/react-components';

export const useFieldsTabHeaderStyles = makeStyles({
    tabsHeaderRow: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: tokens.spacingHorizontalS,
    },
});

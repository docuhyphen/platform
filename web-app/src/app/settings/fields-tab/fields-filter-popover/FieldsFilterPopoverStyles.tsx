import {makeStyles, tokens} from '@fluentui/react-components';

export const useFieldsFilterPopoverStyles = makeStyles({
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalS,
        minWidth: '200px',
        maxWidth: '280px',
    },
});

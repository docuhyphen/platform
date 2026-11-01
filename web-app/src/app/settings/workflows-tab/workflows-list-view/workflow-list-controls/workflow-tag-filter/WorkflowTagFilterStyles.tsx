import {makeStyles, tokens} from '@fluentui/react-components';

export const useWorkflowTagFilterStyles = makeStyles({
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: 'flex',
        flexDirection: 'column',
        gap: tokens.spacingHorizontalSNudge,
        minWidth: '180px',
        maxWidth: '260px',
    },
    filterList: {
        minHeight: '48px',
        maxHeight: '220px',
        overflowY: 'auto',
        display: 'flex',
        flexDirection: 'column',
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: `${tokens.spacingVerticalXS} ${tokens.spacingHorizontalS}`,
    },
});

import {makeStyles, shorthands, tokens} from '@fluentui/react-components';

export const usePendingApprovalItemStyles = makeStyles({
    accordionHeader: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        width: '100%',
        ...shorthands.gap(tokens.spacingHorizontalS),
        paddingRight: tokens.spacingHorizontalS,
    },
    panelContent: {
        display: 'flex',
        flexDirection: 'column',
        ...shorthands.gap(tokens.spacingHorizontalS),
        paddingBottom: tokens.spacingVerticalS,
    },
    actions: {
        display: 'flex',
        ...shorthands.gap(tokens.spacingHorizontalS),
        justifyContent: 'flex-end',
    },
    commentField: {
        width: '100%',
    },
});

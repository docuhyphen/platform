import {makeStyles, tokens} from '@fluentui/react-components';

export const useBlueprintBusinessFieldsTabStyles = makeStyles({
    container: {
        display: 'flex',
        flexDirection: 'column',
        rowGap: tokens.spacingVerticalM,
        paddingTop: tokens.spacingVerticalS,
    },
    hint: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },
});

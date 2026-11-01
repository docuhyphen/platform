import React from 'react';
import {ToggleButton, Tooltip} from '@fluentui/react-components';
import {ViewCardsIcon, ViewTableIcon} from '../IconBundles.tsx';
import {ViewMode} from '../../models/models.tsx';
import {useViewModeToggleStyles} from "./ViewModeToggleStyles.tsx";

interface Props
{
    value: ViewMode;
    onChange: (mode: ViewMode) => void;
}

const ViewModeToggle: React.FC<Props> = ({value, onChange}) =>
{
    const styles = useViewModeToggleStyles();

    return (
    <div id={"view-mode-toggle-div-1"}
         className={styles.root}>
        <Tooltip content="Cards view"
                 relationship="description">
            <ToggleButton
                id={"view-mode-toggle-toggle-button-1"}
                icon={<ViewCardsIcon/>}
                appearance="subtle"
                shape="circular"
                size="small"
                checked={value === 'cards'}
                onClick={() => onChange('cards')}
            />
        </Tooltip>
        <Tooltip content="Table view"
                 relationship="description">
            <ToggleButton
                id={"view-mode-toggle-toggle-button-2"}
                icon={<ViewTableIcon/>}
                appearance="subtle"
                shape="circular"
                size="small"
                checked={value === 'table'}
                onClick={() => onChange('table')}
            />
        </Tooltip>
    </div>
    );
};

export default ViewModeToggle;

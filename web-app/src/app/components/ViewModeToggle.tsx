import React from 'react';
import {ToggleButton, Tooltip} from '@fluentui/react-components';
import {ViewCardsIcon, ViewTableIcon} from './IconBundles.tsx';
import {ViewMode} from '../models/models.tsx';

interface Props
{
    value: ViewMode;
    onChange: (mode: ViewMode) => void;
}

const ViewModeToggle: React.FC<Props> = ({value, onChange}) => (
    <div style={{display: 'flex', gap: '2px'}}>
        <Tooltip content="Cards view" relationship="description">
            <ToggleButton
                icon={<ViewCardsIcon/>}
                appearance="subtle"
                shape="circular"
                size="small"
                checked={value === 'cards'}
                onClick={() => onChange('cards')}
            />
        </Tooltip>
        <Tooltip content="Table view" relationship="description">
            <ToggleButton
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

export default ViewModeToggle;

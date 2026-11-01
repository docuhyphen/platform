import {ReactNode} from 'react';
import {Button, Popover, PopoverSurface, PopoverTrigger, Tooltip} from '@fluentui/react-components';
import {FilterIcon} from '../../../components/IconBundles';
import {useFieldsFilterPopoverStyles} from './FieldsFilterPopoverStyles';

interface Props
{
    triggerId: string;
    surfaceId: string;
    tooltip: string;
    active: boolean;
    onClosed: () => void;
    children: ReactNode;
}

const FieldsFilterPopover = ({triggerId, surfaceId, tooltip, active, onClosed, children}: Props) =>
{
    const styles = useFieldsFilterPopoverStyles();

    return (
        <Popover positioning="below-end"
                 onOpenChange={(_, data) => { if (!data.open) onClosed(); }}>
            <PopoverTrigger disableButtonEnhancement>
                <Tooltip content={tooltip}
                         relationship="description">
                    <Button id={triggerId}
                            icon={<FilterIcon/>}
                            appearance={active ? 'primary' : 'subtle'}
                            shape="circular"/>
                </Tooltip>
            </PopoverTrigger>
            <PopoverSurface id={surfaceId}
                            className={styles.filterPopover}>
                {children}
            </PopoverSurface>
        </Popover>
    );
};

export default FieldsFilterPopover;

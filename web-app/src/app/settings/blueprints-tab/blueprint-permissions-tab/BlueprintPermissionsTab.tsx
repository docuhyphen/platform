import {Checkbox} from '@fluentui/react-components';
import {BlueprintConfig} from '../../../models/models.tsx';
import {BlueprintBooleanConfigKey} from '../blueprintEditorTypes.ts';
import {blueprintPermissionOptions} from './blueprintPermissionOptions.ts';
import {useBlueprintPermissionsTabStyles} from './BlueprintPermissionsTabStyles.tsx';

interface BlueprintPermissionsTabProps
{
    config: BlueprintConfig;
    onToggle: (key: BlueprintBooleanConfigKey, value: boolean) => void;
}

const BlueprintPermissionsTab = ({config, onToggle}: BlueprintPermissionsTabProps) =>
{
    const styles = useBlueprintPermissionsTabStyles();

    return (
        <div id={"blueprint-editor-div-4"}
             className={styles.permissionsContent}>
            {blueprintPermissionOptions.map(option => (
                <Checkbox
                    id={option.id}
                    key={option.key}
                    label={option.label}
                    checked={config[option.key] ?? false}
                    onChange={(_, d) => onToggle(option.key, !!d.checked)}
                />
            ))}
        </div>
    );
};

export default BlueprintPermissionsTab;

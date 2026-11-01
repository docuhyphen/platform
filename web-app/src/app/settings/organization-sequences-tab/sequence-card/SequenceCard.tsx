import {Badge, Text} from '@fluentui/react-components';
import {SequenceDefinitionDto} from '../../../models/models';
import SequenceActionsMenu from '../sequence-actions-menu/SequenceActionsMenu.tsx';
import {sequenceCardMenuIds} from '../sequenceMenuIds.ts';
import {useSequenceCardStyles} from './SequenceCardStyles.tsx';

interface SequenceCardProps
{
    sequence: SequenceDefinitionDto;
    canManage: boolean;
    onEdit: (seq: SequenceDefinitionDto) => void;
    onReset: (seq: SequenceDefinitionDto) => void;
    onDelete: (seq: SequenceDefinitionDto) => void;
}

const SequenceCard = ({sequence, canManage, onEdit, onReset, onDelete}: SequenceCardProps) =>
{
    const styles = useSequenceCardStyles();

    return (
        <div
            id={`organization-sequences-tab-div-4-${sequence.id}`}
            className={styles.sequenceCard}
        >
            <div id={"organization-sequences-tab-div-5"}
                 className={styles.sequenceCardInner}>
                <Text
                    id={"organization-sequences-tab-text-5"}
                    weight="semibold"
                    size={400}
                >
                    {sequence.name}
                </Text>
                <code id={"organization-sequences-tab-code-2"}
                      className={styles.codeToken}>{`{{SEQ:${sequence.key}}}`}</code>
                <div id={"organization-sequences-tab-div-6"}
                     className={styles.badgeRow}>
                    <Badge
                        id={"organization-sequences-tab-badge-1"}
                        appearance="tint"
                        color={sequence.isActive ? 'success' : 'severe'}
                        size="small"
                    >
                        {sequence.isActive ? 'Active' : 'Inactive'}
                    </Badge>
                    <Badge
                        id={"organization-sequences-tab-badge-2"}
                        appearance="tint"
                        color="informative"
                        size="small"
                    >
                        Counter: {sequence.currentValue}
                    </Badge>
                    <Badge
                        id={"organization-sequences-tab-badge-3"}
                        appearance="tint"
                        size="small"
                    >
                        Resets: {sequence.resetPeriod}
                    </Badge>
                    <Text
                        id={"organization-sequences-tab-text-6"}
                        size={200}
                        className={styles.nextText}
                    >
                        Next: <strong id={"organization-sequences-tab-strong-1"}>{sequence.previewValue}</strong>
                    </Text>
                </div>
            </div>
            {canManage && (
                <SequenceActionsMenu sequence={sequence}
                                     ids={sequenceCardMenuIds(sequence.id)}
                                     onEdit={onEdit}
                                     onReset={onReset}
                                     onDelete={onDelete}/>
            )}
        </div>
    );
};

export default SequenceCard;

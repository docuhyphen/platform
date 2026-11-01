import {Badge, Text} from '@fluentui/react-components';
import {useCommunicationTokenHintsStyles} from './CommunicationTokenHintsStyles';

const VISIBLE_HINT_COUNT = 12;

interface Props
{
    hints: string[];
    onInsert: (hint: string) => void;
}

const CommunicationTokenHints = ({hints, onInsert}: Props) =>
{
    const styles = useCommunicationTokenHintsStyles();

    return (
        <div id={"communication-editor-dialog-div-2"}>
            <Text
                id={"communication-editor-dialog-text-1"}
                size={200}
                className={styles.tokenHintLabel}
            >
                Available tokens:
            </Text>
            <div id={"communication-editor-dialog-div-3"}
                 className={styles.tokenHintRow}>
                {hints.slice(0, VISIBLE_HINT_COUNT).map(hint => (
                    <Badge
                        id={`communication-editor-dialog-badge-1-${hint}`}
                        key={hint}
                        appearance="tint"
                        size="small"
                        color="informative"
                        className={styles.tokenBadge}
                        onClick={() => onInsert(hint)}
                    >
                        {hint}
                    </Badge>
                ))}
                {hints.length > VISIBLE_HINT_COUNT && (
                    <Text id={"communication-editor-dialog-text-2"}
                          size={200}
                          className={styles.tokenHintMore}>
                        +{hints.length - VISIBLE_HINT_COUNT} more
                    </Text>
                )}
            </div>
        </div>
    );
};

export default CommunicationTokenHints;

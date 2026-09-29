import {Button, mergeClasses, Text} from "@fluentui/react-components";
import {AddIcon} from "../../../components/IconBundles.tsx";
import TemplateItemActions from "../template-item-actions/TemplateItemActions.tsx";
import {useTemplateKeyedListStyles} from "./TemplateKeyedListStyles.tsx";

export interface TemplateKeyedListItem
{
    name: string;
    detail: string;
    problems: string[];
}

interface TemplateKeyedListProps
{
    id: string;
    noun: string;
    items: TemplateKeyedListItem[];
    readOnly: boolean;
    emptyText: string;
    addLabel: string;
    movable?: boolean;
    highlightedIndex?: number;
    onAdd: () => void;
    onEdit: (index: number) => void;
    onMove?: (index: number, offset: number) => void;
    onRemove: (index: number) => void;
}

const TemplateKeyedList = ({
    id,
    noun,
    items,
    readOnly,
    emptyText,
    addLabel,
    movable,
    highlightedIndex,
    onAdd,
    onEdit,
    onMove,
    onRemove,
}: TemplateKeyedListProps) =>
{
    const styles = useTemplateKeyedListStyles();

    return (
        <div id={id}
             className={styles.panel}>
            {items.length === 0 && (
                <Text id={`${id}-empty`}
                      className={styles.muted}>
                    {emptyText}
                </Text>
            )}
            <ul id={`${id}-list`}
                className={styles.list}>
                {items.map((item, index) => (
                    <li key={`${item.name}-${index}`}
                        id={`${id}-item-${index}`}
                        aria-current={highlightedIndex === index ? "true" : undefined}
                        className={mergeClasses(styles.item, highlightedIndex === index && styles.highlighted)}>
                        <div id={`${id}-item-${index}-summary`}
                             className={styles.summary}>
                            <Text id={`${id}-item-${index}-name`}
                                  weight={"semibold"}>
                                {item.name}
                            </Text>
                            <Text id={`${id}-item-${index}-detail`}
                                  className={styles.muted}>
                                {item.detail}
                            </Text>
                            {item.problems.map((problem, position) => (
                                <Text key={`${problem}-${position}`}
                                      id={`${id}-item-${index}-problem-${position}`}
                                      className={styles.problem}>
                                    {problem}
                                </Text>
                            ))}
                        </div>
                        <TemplateItemActions id={`${id}-item-${index}`}
                                             noun={noun}
                                             name={item.name}
                                             readOnly={readOnly}
                                             canMoveUp={index > 0}
                                             canMoveDown={index < items.length - 1}
                                             onEdit={() => onEdit(index)}
                                             onMove={movable && onMove ? offset => onMove(index, offset) : undefined}
                                             onRemove={() => onRemove(index)}/>
                    </li>
                ))}
            </ul>
            {!readOnly && (
                <Button id={`${id}-add`}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        className={styles.addButton}
                        onClick={onAdd}>
                    {addLabel}
                </Button>
            )}
        </div>
    );
};

export default TemplateKeyedList;

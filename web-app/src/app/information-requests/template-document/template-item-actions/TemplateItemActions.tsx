import {Button, Tooltip} from "@fluentui/react-components";
import {ArrowDownIcon, ArrowUpIcon, DeleteIcon, EditIcon, MoreInfoIcon} from "../../../components/IconBundles.tsx";
import {useTemplateItemActionsStyles} from "./TemplateItemActionsStyles.tsx";

interface TemplateItemActionsProps
{
    id: string;
    noun: string;
    name: string;
    readOnly: boolean;
    canMoveUp?: boolean;
    canMoveDown?: boolean;
    onEdit: () => void;
    onMove?: (offset: number) => void;
    onRemove?: () => void;
}

const TemplateItemActions = ({
    id,
    noun,
    name,
    readOnly,
    canMoveUp,
    canMoveDown,
    onEdit,
    onMove,
    onRemove,
}: TemplateItemActionsProps) =>
{
    const styles = useTemplateItemActionsStyles();
    const editLabel = `${readOnly ? "View" : "Edit"} ${noun} ${name}`;

    return (
        <div id={`${id}-actions`}
             className={styles.actions}>
            <Tooltip content={editLabel}
                     relationship={"label"}>
                <Button id={`${id}-edit`}
                        appearance={"subtle"}
                        shape={"circular"}
                        icon={readOnly ? <MoreInfoIcon/> : <EditIcon/>}
                        onClick={onEdit}/>
            </Tooltip>
            {!readOnly && onMove && (
                <>
                    <Tooltip content={`Move ${noun} ${name} up`}
                             relationship={"label"}>
                        <Button id={`${id}-move-up`}
                                appearance={"subtle"}
                                shape={"circular"}
                                icon={<ArrowUpIcon/>}
                                disabled={!canMoveUp}
                                onClick={() => onMove(-1)}/>
                    </Tooltip>
                    <Tooltip content={`Move ${noun} ${name} down`}
                             relationship={"label"}>
                        <Button id={`${id}-move-down`}
                                appearance={"subtle"}
                                shape={"circular"}
                                icon={<ArrowDownIcon/>}
                                disabled={!canMoveDown}
                                onClick={() => onMove(1)}/>
                    </Tooltip>
                </>
            )}
            {!readOnly && onRemove && (
                <Tooltip content={`Remove ${noun} ${name}`}
                         relationship={"label"}>
                    <Button id={`${id}-remove`}
                            appearance={"subtle"}
                            shape={"circular"}
                            icon={<DeleteIcon/>}
                            onClick={onRemove}/>
                </Tooltip>
            )}
        </div>
    );
};

export default TemplateItemActions;

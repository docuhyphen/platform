import {useTemplateDocument} from "../TemplateDocumentContext.ts";
import {removeItem} from "../templateDraftDocument.ts";
import TemplateKeyedList from "../template-keyed-list/TemplateKeyedList.tsx";
import TemplateGroupDialog from "../template-group-dialog/TemplateGroupDialog.tsx";
import {problemsForItem, useFocusedItem} from "../useFocusedItem.ts";
import {InformationRequestTemplateGroupRequest} from "../../../models/models.tsx";

const describe = (group: InformationRequestTemplateGroupRequest): string =>
{
    const nesting = group.parentGroupKey ? `Inside each ${group.parentGroupKey}` : "Directly in the request";
    const bounds = group.maxOccurrences === undefined
        ? `at least ${group.minOccurrences ?? 0}`
        : `${group.minOccurrences ?? 0} to ${group.maxOccurrences}`;
    return `${nesting}, ${bounds} entries`;
};

const TemplateGroupsPanel = () =>
{
    const {document, readOnly, problems, update} = useTemplateDocument();
    const {editing, setEditing, highlighted} = useFocusedItem("groups");

    return (
        <>
            <TemplateKeyedList id={"template-groups-panel"}
                               noun={"group"}
                               items={document.groups.map((group, index) => ({
                                   name: group.groupKey,
                                   detail: describe(group),
                                   problems: problemsForItem(problems, "groups", index),
                               }))}
                               readOnly={readOnly}
                               emptyText={"No repeatable groups. A group lets a requirement be answered once per entry, such as once per item or per person."}
                               addLabel={"Add group"}
                               highlightedIndex={highlighted}
                               onAdd={() => setEditing("new")}
                               onEdit={index => setEditing(index)}
                               onRemove={index => update(current => ({...current, groups: removeItem(current.groups, index)}))}/>
            {editing !== null && (
                <TemplateGroupDialog groupIndex={editing === "new" ? undefined : editing}
                                     onClose={() => setEditing(null)}/>
            )}
        </>
    );
};

export default TemplateGroupsPanel;

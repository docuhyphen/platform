import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {AssigneeSpecDraft, WorkflowSubjectFieldDto} from "../../../models/models.tsx";
import {useAssigneeBuilderStyles} from "./AssigneeBuilderStyles.tsx";
import {AddIcon} from "../../../components/IconBundles.tsx";
import {buildGroupRefOptions, buildOrganizationRefOptions, createAssignee, toPersonPickerItem} from "./assigneeOptions.ts";
import {useOrganizationDirectory} from "./useOrganizationDirectory.ts";
import AssigneeRow from "./assignee-row/AssigneeRow.tsx";

interface Props
{
    assignees: AssigneeSpecDraft[];
    onChange: (assignees: AssigneeSpecDraft[]) => void;
    label?: string;
    subjectFields?: WorkflowSubjectFieldDto[];
    allowTenantDirectory?: boolean;
}

const AssigneeBuilder = ({
    assignees,
    onChange,
    label,
    subjectFields = [],
    allowTenantDirectory = true,
}: Props) =>
{
    const styles = useAssigneeBuilderStyles();
    const {users, groups} = useOrganizationDirectory(allowTenantDirectory);
    const [personQueries, setPersonQueries] = useState<Record<number, string>>({});

    const update = (index: number, patch: Partial<AssigneeSpecDraft>) =>
        onChange(assignees.map((a, i) => (i === index ? {...a, ...patch} : a)));

    const replace = (index: number, assignee: AssigneeSpecDraft) =>
        onChange(assignees.map((current, currentIndex) => currentIndex === index ? assignee : current));

    const remove = (index: number) => onChange(assignees.filter((_, i) => i !== index));

    const add = () => onChange([
        ...assignees,
        createAssignee("ORGANIZATION_ROLE"),
    ]);

    const organizationRefOptions = buildOrganizationRefOptions(subjectFields);
    const groupRefOptions = buildGroupRefOptions(groups, subjectFields);
    const people = users.map(toPersonPickerItem).filter(person => person.id);

    return (
        <div id={"assignee-builder-div-1"}
             className={styles.container}>
            {label && <Text id={"assignee-builder-text-1"}
                            size={200}
                            weight="semibold">{label}</Text>}

            {assignees.length === 0 && (
                <Text id={"assignee-builder-text-2"}
                      className={styles.emptyHint}
                      size={200}>No assignees added yet.</Text>
            )}

            {assignees.map((a, i) => (
                <AssigneeRow key={i}
                             index={i}
                             assignee={a}
                             allowTenantDirectory={allowTenantDirectory}
                             organizationRefOptions={organizationRefOptions}
                             groupRefOptions={groupRefOptions}
                             people={people}
                             personQuery={personQueries[i] ?? ""}
                             onPersonQueryChange={query => setPersonQueries(current => ({...current, [i]: query}))}
                             onUpdate={patch => update(i, patch)}
                             onReplace={assignee => replace(i, assignee)}
                             onRemove={() => remove(i)} />
            ))}

            <div id={"assignee-builder-div-4"}
                 className={styles.addRow}>
                <Button id={"add-assignee-button"}
                        size="small"
                        shape={"circular"}
                        appearance="secondary"
                        icon={<AddIcon/>}
                        onClick={add}>
                    Add Assignee
                </Button>
            </div>
        </div>
    );
};

export default AssigneeBuilder;

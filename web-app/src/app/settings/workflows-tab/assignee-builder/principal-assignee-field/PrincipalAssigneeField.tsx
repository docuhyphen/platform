import {AssigneeSpecDraft} from "../../../../models/models.tsx";
import SinglePersonPicker from "../../../../components/person-picker/single-person-picker/SinglePersonPicker.tsx";
import {matchesPersonQuery, PersonPickerItem} from "../../../../components/person-picker/personPickerTypes.ts";

interface PrincipalAssigneeFieldProps
{
    index: number;
    assignee: AssigneeSpecDraft;
    people: PersonPickerItem[];
    query: string;
    onQueryChange: (query: string) => void;
    onUpdate: (patch: Partial<AssigneeSpecDraft>) => void;
}

const PrincipalAssigneeField = ({index, assignee, people, query, onQueryChange, onUpdate}: PrincipalAssigneeFieldProps) =>
{
    return (
        <SinglePersonPicker id={`assignee-principal-select-${index}`}
                            size={"small"}
                            people={people.filter(person => matchesPersonQuery(person, query))}
                            query={query}
                            onQueryChange={onQueryChange}
                            onPersonSelect={person => onUpdate({
                                principalId: person?.id ?? "",
                                principalKind: "USER",
                            })}
                            placeholder="Find a user"
                            selectedPersonId={assignee.principalId}
                            noResultsText="No matching organization users found" />
    );
};

export default PrincipalAssigneeField;

import {
    TagPicker,
    TagPickerControl,
    TagPickerGroup,
    TagPickerInput,
    TagPickerList,
    TagPickerOption,
} from "@fluentui/react-tag-picker";
import PersonOption from "../person-option/PersonOption.tsx";
import PersonTag from "../person-tag/PersonTag.tsx";
import {getPersonName, PersonPickerItem} from "../personPickerTypes.ts";
import {useMultiPersonPickerStyles} from "./MultiPersonPickerStyles.tsx";

interface Props
{
    id: string;
    people: PersonPickerItem[];
    selectedPeople: PersonPickerItem[];
    onSelectionChange: (selectedIds: string[]) => void;
    query: string;
    onQueryChange: (query: string) => void;
    placeholder: string;
    disabled?: boolean;
    noResultsText?: string;
}

const MultiPersonPicker = ({
    id,
    people,
    selectedPeople,
    onSelectionChange,
    query,
    onQueryChange,
    placeholder,
    disabled = false,
    noResultsText = "No matching people found",
}: Props) =>
{
    const styles = useMultiPersonPickerStyles();
    const selectedIds = selectedPeople.map(person => person.id);
    const availablePeople = people.filter(person => !selectedIds.includes(person.id));

    return (
        <TagPicker
            selectedOptions={selectedIds}
            onOptionSelect={(_, data) => onSelectionChange(data.selectedOptions)}
        >
            <TagPickerControl id={`${id}-control`}>
                <TagPickerGroup id={`${id}-selected-people`}>
                    {selectedPeople.map(person => (
                        <PersonTag
                            id={`${id}-tag-${person.id}`}
                            key={person.id}
                            value={person.id}
                            person={person}
                        />
                    ))}
                </TagPickerGroup>
                <TagPickerInput
                    id={id}
                    value={query}
                    onChange={event => onQueryChange(event.target.value)}
                    placeholder={placeholder}
                    disabled={disabled}
                />
            </TagPickerControl>
            <TagPickerList id={`${id}-list`}>
                {availablePeople.map(person => (
                    <TagPickerOption
                        id={`${id}-option-${person.id}`}
                        key={person.id}
                        value={person.id}
                        text={`${getPersonName(person)} ${person.email}`}
                    >
                        <PersonOption
                            id={`${id}-persona-${person.id}`}
                            person={person}
                        />
                    </TagPickerOption>
                ))}
                {availablePeople.length === 0 && query.trim() && (
                    <div
                        id={`${id}-empty-option`}
                        className={styles.emptyState}
                        role="status"
                    >
                        {noResultsText}
                    </div>
                )}
            </TagPickerList>
        </TagPicker>
    );
};

export default MultiPersonPicker;

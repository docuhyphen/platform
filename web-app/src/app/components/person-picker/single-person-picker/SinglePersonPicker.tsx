import {Combobox, ComboboxProps, Option, Spinner} from "@fluentui/react-components";
import PersonOption from "../person-option/PersonOption.tsx";
import {getPersonName, PersonPickerItem} from "../personPickerTypes.ts";

interface Props
{
    id: string;
    people: PersonPickerItem[];
    query: string;
    onQueryChange: (query: string) => void;
    onPersonSelect: (person: PersonPickerItem | null) => void;
    placeholder: string;
    selectedPersonId?: string | null;
    loading?: boolean;
    disabled?: boolean;
    freeform?: boolean;
    noResultsText?: string;
    children?: React.ReactNode;
    onSpecialOptionSelect?: (value: string) => boolean;
}

const SinglePersonPicker = ({
    id,
    people,
    query,
    onQueryChange,
    onPersonSelect,
    placeholder,
    selectedPersonId,
    loading = false,
    disabled = false,
    freeform = false,
    noResultsText = "No matching people found",
    children,
    onSpecialOptionSelect,
}: Props) =>
{
    const handleSelect: ComboboxProps["onOptionSelect"] = (_, data) =>
    {
        const person = people.find(candidate => candidate.id === data.optionValue) ?? null;
        if (!person && data.optionValue && onSpecialOptionSelect?.(data.optionValue)) return;
        onPersonSelect(person);
        if (person) onQueryChange(`${getPersonName(person)} (${person.email})`);
    };

    return (
        <Combobox
            id={id}
            placeholder={placeholder}
            value={query}
            selectedOptions={selectedPersonId ? [selectedPersonId] : []}
            onChange={event =>
            {
                if (selectedPersonId) onPersonSelect(null);
                onQueryChange(event.target.value);
            }}
            onOptionSelect={handleSelect}
            disabled={disabled}
            freeform={freeform}
        >
            {loading && (
                <Option
                    id={`${id}-loading-option`}
                    value="__loading__"
                    text="Loading people"
                    disabled
                >
                    <Spinner
                        id={`${id}-loading-spinner`}
                        size="tiny"
                        label="Loading people..."
                    />
                </Option>
            )}
            {!loading && people.map(person => (
                <Option
                    id={`${id}-option-${person.id}`}
                    key={person.id}
                    value={person.id}
                    text={`${getPersonName(person)} ${person.email}`}
                >
                    <PersonOption
                        id={`${id}-persona-${person.id}`}
                        person={person}
                    />
                </Option>
            ))}
            {!loading && people.length === 0 && query.trim() && !children && (
                <Option
                    id={`${id}-empty-option`}
                    value="__empty__"
                    text={noResultsText}
                    disabled
                >
                    {noResultsText}
                </Option>
            )}
            {children}
        </Combobox>
    );
};

export default SinglePersonPicker;

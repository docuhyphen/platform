import {Avatar, Tag} from "@fluentui/react-components";
import {getPersonName, PersonPickerItem} from "../personPickerTypes.ts";

interface Props
{
    id: string;
    person: PersonPickerItem;
    value: string;
}

const PersonTag = ({id, person, value}: Props) =>
{
    const name = getPersonName(person);

    return (
        <Tag
            id={id}
            value={value}
            shape="circular"
            dismissible
            media={
                <Avatar
                    id={`${id}-avatar`}
                    name={name}
                    size={20}
                    image={person.avatarUrl ? {src: person.avatarUrl} : undefined}
                />
            }
        >
            {name}
        </Tag>
    );
};

export default PersonTag;

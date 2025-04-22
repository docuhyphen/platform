import {Button, Text} from "@fluentui/react-components";
import * as React from "react";
import {GroupAddIcon, PersonAddIcon} from "../../components/IconBundles.tsx";

const OrganizationPeopleTab = () =>
{
    return <>
        <div>
            <Button icon={<PersonAddIcon/>}
                    shape={"circular"}>
                Add Person
            </Button>
        </div>
    </>
}

export default OrganizationPeopleTab;
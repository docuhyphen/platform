import {Button, Text} from "@fluentui/react-components";
import * as React from "react";
import {GroupAddIcon} from "../../components/IconBundles.tsx";

const OrganizationGroupsTab = () =>
{
    return <>
        <div>
            <p> Groups can be departments, teams, or a group of users withing a team.</p>
            <Button icon={<GroupAddIcon/>}
                    shape={"circular"}>
                Create Group
            </Button>
        </div>
    </>
}

export default OrganizationGroupsTab;
import React, {useEffect, useState} from 'react';
import useToken from "../../context/useToken.tsx";
import {fetchSignedInUserAppUserSharingSessions} from "../../services/api.ts";
import {Card, List, ListItem} from "@fluentui/react-components";

const SharingSessionList: React.FC = () =>
{

    const token = useToken()
    const [sharingSessions, setSharingSessions] = useState([]);
    const [loadingSharingSessions, setLoadingSharingSessions] = useState(true);

    const fetchSharingSessions = async () =>
    {
        try
        {
            const sharingSessions = await fetchSignedInUserAppUserSharingSessions(token);
            setSharingSessions(sharingSessions);
        }
        catch (error)
        {
            console.error(error);
        }
        finally
        {
            setLoadingSharingSessions(false);
        }
    }

    useEffect(() =>
    {

        fetchSharingSessions()
    }, [])

    const onSelectionChange = React.useCallback((_, data) =>
    {
        // setSelectedItems(data.selectedItems);
    }, []);

    const onFocus = React.useCallback((event) =>
    {
        // Ignore bubbled up events from the children
        if (event.target !== event.currentTarget)
        {
            return;
        }
        // setSelectedItems([event.target.dataset.value]);
    }, []);

    return (
        <div>
            <h1>Sharing Session List {sharingSessions.length}</h1>
            <List
                selectionMode="single"
                navigationMode="composite"
                // selectedItems={selectedItems}
                onSelectionChange={onSelectionChange}
            >
                {sharingSessions.map((session: any) => (
                    <ListItem
                        key={session.id}
                        value={session.id}
                        // className={mergeClasses(
                        //     classes.item,
                        //     selectedItems.includes(name) && classes.itemSelected
                        // )}
                        data-value={session.id}
                        onFocus={onFocus}
                        checkmark={null}
                    >
                        <Card>
                            {session.sessionName}
                        {/*    ToDo: add mark as read to the item menu*/}
                        </Card>
                    </ListItem>
                ))}
            </List>
        </div>
    );
};

export default SharingSessionList;
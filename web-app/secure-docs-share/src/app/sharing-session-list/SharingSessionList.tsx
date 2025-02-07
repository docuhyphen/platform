import React, {useEffect, useState} from 'react';
import useToken from "../../context/useToken.tsx";
import {fetchSignedInUserAppUserSharingSessions} from "../../services/api.ts";
import {Avatar, Divider, List, ListItem, makeStyles, typographyStyles} from "@fluentui/react-components";
import "./SharingSessionList.css"

const useStyles = makeStyles({
    caption2: typographyStyles.caption2,
    caption1: typographyStyles.caption1,
    body1Strong: typographyStyles.body1Strong,
});
const formatDate = (dateString: string): string =>
{
    const date = new Date(dateString);
    return date.toLocaleDateString('en-GB');
};

interface SharingSessionListProps
{
    onSelectionChange: (sessionId: string) => void;
}

const SharingSessionList: React.FC<SharingSessionListProps> = ({onSelectionChange}) =>
{
    const styles = useStyles();
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

    const handleSelectionChange = (_, data) =>
    {
        onSelectionChange(data.selectedItems[0]);
    };

    // const onSelectionChange = React.useCallback((_, data) =>
    // {
    //     console.log("Selected item: ", data.selectedItems);
    //     // setSelectedItems(data.selectedItems);
    // }, []);

    const onFocus = React.useCallback((event) =>
    {
        // Ignore bubbled up events from the children
        if (event.target !== event.currentTarget)
        {
            return;
        }
        // setSelectedItems([event.target.dataset.value]);
    }, []);

    const listItemCard = (session: any) =>
    {
        return <div className={"list-card"}>
            <section className={"list-card-item"}>
                <span>
                    <Avatar name={session.recipientEmail}/>
                </span>
                <span className={"list-card-item-details"}>
                    <div className={styles.caption1}>{session.recipientEmail}</div>
                    <div className={"list-card-item-row"}>
                        <div className={styles.body1Strong}>{session.sessionName}</div>
                        <div className={styles.caption2}>{formatDate(session.createdDate)}</div>
                    </div>
                    <div>
                        <div className={styles.caption1}> {session.description} </div>
                    </div>
                </span>
            </section>
        </div>
    }

    return (
        <section id={"sharing-sessions-list-container"}>
            <div id={"sharing-sessions-list-header"}>

            </div>
            <List
                id={"sharing-sessions-list-body"}
                selectionMode="single"
                navigationMode="composite"
                // selectedItems={selectedItems}
                onSelectionChange={handleSelectionChange}
            >
                {sharingSessions.map((session: any) => (
                    <>
                        <ListItem
                            className={"sharing-sessions-list-item"}
                        key={session.id}
                        value={session.id}
                        // className={mergeClasses(
                            //     "sharing-sessions-list-item",
                        //     selectedItems.includes(name) && classes.itemSelected
                        // )}
                        data-value={session.id}
                        onFocus={onFocus}
                        checkmark={null}
                    >
                            {listItemCard(session)}
                    </ListItem>
                        <Divider/>
                    </>
                ))}
            </List>
        </section>
    );
}

export default SharingSessionList;

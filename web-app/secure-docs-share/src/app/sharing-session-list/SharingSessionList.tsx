import React, {useEffect, useState} from 'react';
import useToken from "../../context/useToken.tsx";
import {fetchSignedInUserAppUserSharingSessions} from "../../services/api.ts";
import {
    Avatar, Button,
    Divider,
    Field,
    List,
    ListItem,
    makeStyles, Menu, MenuItem, MenuList, MenuPopover, MenuTrigger,
    SearchBox, Tooltip,
    typographyStyles
} from "@fluentui/react-components";
import "./SharingSessionList.css"
import {
    ArrowSortDownLinesFilled,
    ArrowSortDownLinesRegular,
    ArrowSortUpLinesFilled, ArrowSortUpLinesRegular,
    bundleIcon,
    CutFilled,
    CutRegular,
    EditFilled,
    EditRegular,
    FilterFilled,
    FilterRegular
} from "@fluentui/react-icons";

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

    const FilterIcon = bundleIcon(FilterFilled, FilterRegular);
    const SortDownIcon = bundleIcon(ArrowSortDownLinesFilled, ArrowSortDownLinesRegular);
    const SortUpIcon = bundleIcon(ArrowSortUpLinesFilled, ArrowSortUpLinesRegular);

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
            <div id={"sharing-sessions-list-header"}>
                <Field id={"filter-search-field"}>
                    <SearchBox/>
                </Field>
                <Menu>
                    <MenuTrigger>
                        <Tooltip content="Filter sharing sessions" relationship={"description"}>
                            <Button icon={<FilterIcon/>} appearance={"subtle"}/>
                        </Tooltip>
                    </MenuTrigger>
                    <MenuPopover>
                        <MenuList>
                            <MenuItem icon={<FilterIcon />}>
                                Cut
                            </MenuItem>
                            <MenuItem icon={<FilterIcon />}>
                                Edit
                            </MenuItem>
                        </MenuList>
                    </MenuPopover>
                </Menu>
                <Tooltip content="Sort descending" relationship={"description"}>
                    <Button icon={<SortDownIcon/>} appearance={"subtle"}/>
                </Tooltip>
            </div>
        </section>
    );
}

export default SharingSessionList;

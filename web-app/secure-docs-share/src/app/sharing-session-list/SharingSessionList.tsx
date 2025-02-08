import React, {useEffect, useState} from 'react';
import useToken from "../../context/useToken.tsx";
import {fetchSignedInUserAppUserSharingSessions} from "../../services/api.ts";
import {
    Avatar,
    Button,
    Divider,
    Field,
    List,
    ListItem,
    makeStyles,
    Menu,
    MenuItem,
    MenuList,
    MenuPopover,
    MenuTrigger,
    SearchBox,
    Tooltip,
    typographyStyles
} from "@fluentui/react-components";
import "./SharingSessionList.css"
import {
    ArrowSortDownLinesFilled,
    ArrowSortDownLinesRegular,
    ArrowSortUpLinesFilled,
    ArrowSortUpLinesRegular,
    bundleIcon,
    FilterFilled,
    FilterRegular
} from "@fluentui/react-icons";
import {formatDate} from "../helpers.ts";

const useStyles = makeStyles({
    caption2: typographyStyles.caption2,
    caption1: typographyStyles.caption1,
    body1Strong: typographyStyles.body1Strong,
});

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
    const [selectedItems, setSelectedItems] = useState<string[]>([]);

    const fetchSharingSessions = async () =>
    {
        try
        {
            const sharingSessions = await fetchSignedInUserAppUserSharingSessions(token);

            if(sharingSessions && sharingSessions.length)
            {
                setSharingSessions(sharingSessions);
                setSelectedItems([sharingSessions[0].id]);
                onSelectionChange(sharingSessions[0].id);
            }
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
        setSelectedItems(data.selectedItems);
        onSelectionChange(data.selectedItems[0]);
    };

    const FilterIcon = bundleIcon(FilterFilled, FilterRegular);
    const SortDownIcon = bundleIcon(ArrowSortDownLinesFilled, ArrowSortDownLinesRegular);
    const SortUpIcon = bundleIcon(ArrowSortUpLinesFilled, ArrowSortUpLinesRegular);

    const onFocus = React.useCallback((event) =>
    {
        if (event.target !== event.currentTarget)
        {
            return;
        }
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
                selectedItems={selectedItems}
                onSelectionChange={handleSelectionChange}
            >
                {sharingSessions.map((session: any) => (
                    <>
                        <ListItem
                            className={selectedItems.includes(session.id) ? "sharing-sessions-list-selected-item" : ""}
                            key={session.id}
                            value={session.id}
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